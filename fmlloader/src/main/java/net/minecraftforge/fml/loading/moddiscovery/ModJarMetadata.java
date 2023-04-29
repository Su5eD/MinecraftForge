/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.ModuleJarMetadata;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ModJarMetadata implements JarMetadata {
    private static final String MODULE_INFO = "module-info.class";
    private static final String AUTOMATIC_MODULE_NAME = "Automatic-Module-Name";
    private static final String MINECRAFT_MODULE = "minecraft";

    private IModFile modFile;
    private ModuleDescriptor descriptor;

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static Optional<IModFile> buildFile(IModLocator locator, Predicate<SecureJar> jarTest, BiPredicate<String, String> filter, Path... files) {
        return buildFile(j->new ModFile(j, locator, ModFileParser::modsTomlParser), jarTest, filter, files);
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static IModFile buildFile(IModLocator locator, Path... files) {
        return buildFile(locator, j->true, (a,b) -> true, files).orElseThrow(()->new IllegalArgumentException("Failed to find valid JAR file"));
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static Optional<IModFile> buildFile(Function<SecureJar, IModFile> mfConstructor, Predicate<SecureJar> jarTest, BiPredicate<String, String> filter, Path... files) {
        var mjm = new ModJarMetadata();
        var sj = SecureJar.from(()->ModFile.DEFAULTMANIFEST, j->mjm, filter, files);
        if (jarTest.test(sj)) {
            var mf = mfConstructor.apply(sj);
            mjm.setModFile(mf);
            return Optional.of(mf);
        } else {
            return Optional.empty();
        }
    }

    ModJarMetadata() {
    }

    public void setModFile(IModFile file) {
        this.modFile = file;
    }

    @Override
    public String name() {
        return descriptor().name();
    }

    @Override
    public String version() {
        return modFile.getModFileInfo().versionString();
    }

    @Override
    public ModuleDescriptor descriptor() {
        if (descriptor == null) {
            SecureJar secureJar = modFile.getSecureJar();
            descriptor = secureJar.moduleDataProvider().findFile(MODULE_INFO)
                .map(uri -> {
                    JarMetadata metadata = new ModuleJarMetadata(uri, secureJar.getPackages());
                    ModuleDescriptor jarDescriptor = metadata.descriptor();
                    var builder = wrapDescriptor(jarDescriptor);
                    builder.version(version());
                    builder.requires(MINECRAFT_MODULE);
                    return builder.build();
                })
                .orElseGet(() -> {
                    var name = Optional.ofNullable(secureJar.moduleDataProvider().getManifest().getMainAttributes().getValue(AUTOMATIC_MODULE_NAME))
                        .orElseGet(() -> modFile.getModFileInfo().moduleName());
                    var bld = ModuleDescriptor.newAutomaticModule(name)
                        .version(version())
                        .packages(modFile.getSecureJar().getPackages());
                    secureJar.getProviders().forEach(p -> bld.provides(p.serviceName(), p.providers()));
                    modFile.getModFileInfo().usesServices().forEach(bld::uses);
                    return bld.build();
                });
        }
        return descriptor;
    }

    public IModFile modFile() {
        return modFile;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ModJarMetadata) obj;
        return Objects.equals(this.modFile, that.modFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modFile);
    }

    @Override
    public String toString() {
        return "ModJarMetadata[" +"modFile=" + modFile + ']';
    }

    private ModuleDescriptor.Builder wrapDescriptor(ModuleDescriptor descriptor) {
        var builder = ModuleDescriptor.newModule(descriptor.name(), descriptor.modifiers())
            .packages(descriptor.packages());
        descriptor.version().ifPresent(builder::version);
        descriptor.requires().forEach(builder::requires);
        descriptor.exports().forEach(builder::exports);
        descriptor.opens().forEach(builder::opens);
        descriptor.uses().forEach(builder::uses);
        descriptor.provides().forEach(builder::provides);
        return builder;
    } 
}
