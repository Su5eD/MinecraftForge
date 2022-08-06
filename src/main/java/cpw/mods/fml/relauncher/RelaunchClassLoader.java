/*
 * The FML Forge Mod Loader suite.
 * Copyright (C) 2012 cpw
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package cpw.mods.fml.relauncher;

import cpw.mods.fml.common.FMLLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;

public class RelaunchClassLoader extends URLClassLoader
{
    private List<URL> sources;
    private ClassLoader parent;

    private List<IClassTransformer> transformers;
    private IClassNameMapper nameTransformer;
    private Map<String, Class> cachedClasses;
    private Set<String> invalidClasses;

    private Set<String> classLoaderExceptions = new HashSet<String>();
    private Set<String> transformerExceptions = new HashSet<String>();
    private Map<Package,Manifest> packageManifests = new HashMap<Package,Manifest>();
    private URLClassLoader childClassLoader;

    private static Manifest EMPTY = new Manifest();

    private static final String[] RESERVED = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

    private static final boolean DEBUG_CLASSLOADING = Boolean.parseBoolean(System.getProperty("fml.debugClassLoading", "false"));

    public RelaunchClassLoader(URL[] sources)
    {
        super(sources, null);
        this.sources = new ArrayList<URL>(Arrays.asList(sources));
        this.parent = getClass().getClassLoader();
        this.cachedClasses = new HashMap<String,Class>(1000);
        this.invalidClasses = new HashSet<String>(1000);
        this.transformers = new ArrayList<IClassTransformer>(2);
//        ReflectionHelper.setPrivateValue(ClassLoader.class, null, this, "scl");
        Thread.currentThread().setContextClassLoader(this);

        // standard classloader exclusions
        addClassLoaderExclusion("java.");
        addClassLoaderExclusion("sun.");
        addClassLoaderExclusion("org.lwjgl.");
        addClassLoaderExclusion("cpw.mods.fml.relauncher.");
        addClassLoaderExclusion("net.minecraftforge.classloading.");

        // standard transformer exclusions
        addTransformerExclusion("javax.");
        addTransformerExclusion("org.objectweb.asm.");
        addTransformerExclusion("com.google.common.");
        addTransformerExclusion("cpw.mods.fml.common.asm.transformers.");
    }

    public void registerTransformer(String transformerClassName)
    {
        try
        {
            IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName).newInstance();
            transformers.add(transformer);
            if (transformer instanceof IClassNameMapper && nameTransformer == null)
            {
                nameTransformer = (IClassNameMapper) transformer;
            }
        }
        catch (Exception e)
        {
            FMLRelaunchLog.log(Level.SEVERE, e, "A critical problem occured registering the ASM transformer class %s", transformerClassName);
        }
    }
    
    private String unmapClassName(final String name)
    {
        return nameTransformer != null ? nameTransformer.unmapClassName(name) : name; 
    }
    
    private String mapClassName(final String name)
    {
        return nameTransformer != null ? nameTransformer.mapClassName(name) : name;
    }
    
    public void setChildClassLoader(URLClassLoader ucl)
    {
        childClassLoader = ucl;
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException
    {
        if (invalidClasses.contains(name))
        {
            throw new ClassNotFoundException(name);
        }
        for (String st : classLoaderExceptions)
        {
            if (name.startsWith(st))
            {
                return parent.loadClass(name);
            }
        }

        if (cachedClasses.containsKey(name))
        {
            return cachedClasses.get(name);
        }

        for (String st : transformerExceptions)
        {
            if (name.startsWith(st))
            {
                try
                {
                    Class<?> cl = super.findClass(name);
                    cachedClasses.put(name, cl);
                    return cl;
                }
                catch (ClassNotFoundException e)
                {
                    invalidClasses.add(name);
                    throw e;
                }
            }
        }

        try
        {
            final String mappedName = mapClassName(name);
            if (cachedClasses.containsKey(mappedName)) return cachedClasses.get(mappedName);
            
            final String unmappedName = unmapClassName(name);
                        
            CodeSigner[] signers = null;
            int lastDot = unmappedName.lastIndexOf('.');
            String pkgname = lastDot == -1 ? "" : unmappedName.substring(0, lastDot);
            String fName = unmappedName.replace('.', '/').concat(".class");
            String pkgPath = pkgname.replace('.', '/');
            URLConnection urlConnection = findCodeSourceConnectionFor(fName);
            if (urlConnection instanceof JarURLConnection && lastDot > -1)
            {
                JarURLConnection jarUrlConn = (JarURLConnection)urlConnection;
                JarFile jf = jarUrlConn.getJarFile();
                if (jf != null && jf.getManifest() != null)
                {
                    Manifest mf = jf.getManifest();
                    JarEntry ent = jf.getJarEntry(fName);
                    Package pkg = getPackage(pkgname);
                    getClassBytes(unmappedName);
                    signers = ent.getCodeSigners();
                    if (pkg == null)
                    {
                        pkg = definePackage(pkgname, mf, jarUrlConn.getJarFileURL());
                        packageManifests.put(pkg, mf);
                    }
                    else
                    {
                        if (pkg.isSealed() && !pkg.isSealed(jarUrlConn.getJarFileURL()))
                        {
                            FMLLog.severe("The jar file %s is trying to seal already secured path %s", jf.getName(), pkgname);
                        }
                        else if (isSealed(pkgname, mf))
                        {
                            FMLLog.severe("The jar file %s has a security seal for path %s, but that path is defined and not secure", jf.getName(), pkgname);
                        }
                    }
                }
            }
            else if (lastDot > -1)
            {
                Package pkg = getPackage(pkgname);
                if (pkg == null)
                {
                    pkg = definePackage(pkgname, null, null, null, null, null, null, null);
                    packageManifests.put(pkg, EMPTY);
                }
                else if (pkg.isSealed())
                {
                    FMLLog.severe("The URL %s is defining elements for sealed path %s", urlConnection.getURL(), pkgname);
                }
            }
            byte[] basicClass = getClassBytes(unmappedName);
            byte[] transformedClass = runTransformers(unmappedName, basicClass);
            Class<?> cl = defineClass(mappedName, transformedClass, 0, transformedClass.length, new CodeSource(urlConnection.getURL(), signers));
            cachedClasses.put(mappedName, cl);
            return cl;
        }
        catch (Throwable e)
        {
            invalidClasses.add(name);
            if (DEBUG_CLASSLOADING)
            {
                FMLLog.log(Level.FINEST, e, "Exception encountered attempting classloading of %s", name);
            }
            throw new ClassNotFoundException(name, e);
        }
    }

    private boolean isSealed(String path, Manifest man)
    {
        Attributes attr = man.getAttributes(path);
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }

    @Override
    public URL findResource(String name)
    {
        URL childResource = childClassLoader != null ? childClassLoader.findResource(name) : null;
        return childResource != null ? childResource : super.findResource(name);
    }

    @Override
    public URL getResource(String name)
    {
        URL childResource = childClassLoader != null ? childClassLoader.getResource(name) : null;
        return childResource != null ? childResource : super.getResource(name);
    }

    private URLConnection findCodeSourceConnectionFor(String name)
    {
        URL res = findResource(name);
        if (res != null)
        {
            try
            {
                return res.openConnection();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            return null;
        }
    }

    private byte[] runTransformers(String name, byte[] basicClass)
    {
        for (IClassTransformer transformer : transformers)
        {
            basicClass = transformer.transform(name, basicClass);
        }
        return basicClass;
    }

    @Override
    public void addURL(URL url)
    {
        super.addURL(url);
        sources.add(url);
    }

    public List<URL> getSources()
    {
        List<URL> urls = new ArrayList<>(sources);
        if (childClassLoader != null) urls.addAll(Arrays.asList(childClassLoader.getURLs()));
        return urls;
    }


    private byte[] readFully(InputStream stream)
    {
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(stream.available());
            int r;
            while ((r = stream.read()) != -1)
            {
                bos.write(r);
            }

            return bos.toByteArray();
        }
        catch (Throwable t)
        {
            FMLLog.log(Level.WARNING, t, "Problem loading class");
            return new byte[0];
        }
    }

    public List<IClassTransformer> getTransformers()
    {
        return Collections.unmodifiableList(transformers);
    }

    private void addClassLoaderExclusion(String toExclude)
    {
        classLoaderExceptions.add(toExclude);
    }

    void addTransformerExclusion(String toExclude)
    {
        transformerExceptions.add(toExclude);
    }

    public byte[] getClassBytes(String name) throws IOException
    {
        if (name.indexOf('.') == -1)
        {
            for (String res : RESERVED)
            {
                if (name.toUpperCase(Locale.ENGLISH).startsWith(res))
                {
                    byte[] data = getClassBytes("_" + name);
                    if (data != null)
                    {
                        return data;
                    }
                }
            }
        }

        InputStream classStream = null;
        try
        {
            URL classResource = findResource(name.replace('.', '/').concat(".class"));
            if (classResource == null)
            {
                if (DEBUG_CLASSLOADING)
                {
                    FMLLog.finest("Failed to find class resource %s", name.replace('.', '/').concat(".class"));
                }
                return null;
            }
            classStream = classResource.openStream();
            if (DEBUG_CLASSLOADING)
            {
                FMLLog.finest("Loading class %s from resource %s", name, classResource.toString());
            }
            return readFully(classStream);
        }
        finally
        {
            if (classStream != null)
            {
                try
                {
                    classStream.close();
                }
                catch (IOException e)
                {
                    // Swallow the close exception
                }
            }
        }
    }
}
