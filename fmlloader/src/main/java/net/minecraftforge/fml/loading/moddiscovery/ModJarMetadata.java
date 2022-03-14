package net.minecraftforge.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.jetbrains.annotations.Nullable;

import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ModJarMetadata implements JarMetadata {
    @Nullable
    private final BiPredicate<String, String> filter;
    private IModFile modFile;
    private List<SecureJar.Provider> providers;
    private ModuleDescriptor descriptor;

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static Optional<IModFile> buildFile(IModLocator locator, Predicate<SecureJar> jarTest, BiPredicate<String, String> filter, Path... files) {
        return buildFile(j->new ModFile(j, locator, ModFileParser::modsTomlParser), jarTest, filter, files);
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static IModFile buildFile(IModLocator locator, Path... files) {
        return buildFile(locator, j->true, null, files).orElseThrow(()->new IllegalArgumentException("Failed to find valid JAR file"));
    }

    // TODO: Remove helper functions to cleanup api
    @Deprecated(forRemoval = true, since="1.18")
    static Optional<IModFile> buildFile(Function<SecureJar, IModFile> mfConstructor, Predicate<SecureJar> jarTest, BiPredicate<String, String> filter, Path... files) {
        var mjm = new ModJarMetadata(filter);
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
        this(null);
    }

    ModJarMetadata(final BiPredicate<String, String> filter) {
        this.filter = filter;
    }

    public void setModFile(IModFile file) {
        this.modFile = file;
    }

    @Override
    public String name() {
        return modFile.getModFileInfo().moduleName();
    }

    @Override
    public String version() {
        return modFile.getModFileInfo().versionString();
    }

    @Override
    public ModuleDescriptor descriptor() {
        if (descriptor != null) return descriptor;
        var bld = ModuleDescriptor.newAutomaticModule(name())
                .version(version())
                .packages(modFile.getSecureJar().getPackages());
        providers().forEach(p -> bld.provides(p.serviceName(), p.providers()));
        modFile.getModFileInfo().usesServices().forEach(bld::uses);
        descriptor = bld.build();
        return descriptor;
    }

    @Override
    public List<SecureJar.Provider> providers() {
        if (providers == null) {
            providers = JarMetadata.getProviders(modFile.getSecureJar(), filter);
        }
        return providers;
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
}
