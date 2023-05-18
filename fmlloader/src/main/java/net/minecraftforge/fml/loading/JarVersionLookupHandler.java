/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading;

import java.util.Optional;

/**
 * Finds Version data from a package, with possible default values
 */
public class JarVersionLookupHandler {
    public static Optional<String> getImplementationVersion(final String pkgName) {
        // Note that with Java 9, you'll probably want the module's version data, hence pulling this out
        final String pkgVersion = Package.getPackage(pkgName).getImplementationVersion();
        return Optional.ofNullable(pkgVersion);
    }

    public static Optional<String> getSpecificationVersion(final String pkgName) {
        // Note that with Java 9, you'll probably want the module's version data, hence pulling this out
        final String pkgVersion = Package.getPackage(pkgName).getSpecificationVersion();
        return Optional.ofNullable(pkgVersion);
    }

    public static Optional<String> getImplementationVersion(final Class<?> clazz) {
        // With java 9 we'll use the module's version if it exists in preference.
        return Optional.ofNullable(clazz.getPackage().getImplementationVersion())
            .or(() -> clazz.getModule().getDescriptor().rawVersion());
    }

    public static Optional<String> getImplementationTitle(final Class<?> clazz) {
        final String pkgVersion = clazz.getPackage().getImplementationTitle();
        return Optional.ofNullable(pkgVersion);
    }

    public static Optional<String> getSpecificationVersion(final Class<?> clazz) {
        // With java 9 we'll use the module's version if it exists in preference.
        return Optional.ofNullable(clazz.getPackage().getSpecificationVersion())
            .or(() -> clazz.getModule().getDescriptor().rawVersion());
    }

    public static String findLibraryVersion(final String name, final Class<?> clazz) {
        return getSpecificationVersion(clazz).orElseThrow(() -> new RuntimeException("Missing " + name + " version"));
    }

    // From java.lang.Package#isCompatibleWith
    public static boolean isVersionCompatible(String version, String desired) throws NumberFormatException {
        if (version == null || version.isEmpty()) {
            throw new NumberFormatException("Empty version string");
        }

        String[] sa = version.split("\\.", -1);
        int[] si = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            si[i] = Integer.parseInt(sa[i]);
            if (si[i] < 0)
                throw forInputString(String.valueOf(si[i]), 10);
        }

        String[] da = desired.split("\\.", -1);
        int[] di = new int[da.length];
        for (int i = 0; i < da.length; i++) {
            di[i] = Integer.parseInt(da[i]);
            if (di[i] < 0)
                throw forInputString(String.valueOf(di[i]), 10);
        }

        int len = Math.max(di.length, si.length);
        for (int i = 0; i < len; i++) {
            int d = i < di.length ? di[i] : 0;
            int s = i < si.length ? si[i] : 0;
            if (s < d)
                return false;
            if (s > d)
                return true;
        }
        return true;
    }

    private static NumberFormatException forInputString(String s, int radix) {
        return new NumberFormatException("For input string: \"" + s + "\"" +
            (radix == 10 ?
                "" :
                " under radix " + radix));
    }
}
