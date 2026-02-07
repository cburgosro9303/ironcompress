package io.ironcompress.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;

/**
 * Package-private: loads the native library and provides the {@link SymbolLookup}.
 */
final class NativeLoader {

    private static final System.Logger LOG = System.getLogger(NativeLoader.class.getName());
    private static final SymbolLookup LIB;

    static {
        String libName = resolveLibraryName();
        Path libPath = resolveLibraryPath(libName);
        LOG.log(System.Logger.Level.DEBUG, "Resolving native library: name={0}, path={1}", libName, libPath);
        LIB = SymbolLookup.libraryLookup(libPath, Arena.ofAuto());
        LOG.log(System.Logger.Level.INFO, "Native library loaded: {0}", libPath);
    }

    private NativeLoader() {}

    static SymbolLookup lookup() {
        return LIB;
    }

    private static String resolveLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "libironcompress.dylib";
        } else if (os.contains("win")) {
            return "ironcompress.dll";
        } else {
            return "libironcompress.so";
        }
    }

    private static Path resolveLibraryPath(String libName) {
        String explicit = System.getProperty("native.lib.path");
        if (explicit != null && !explicit.isEmpty()) {
            return Path.of(explicit).resolve(libName).toAbsolutePath();
        }
        return Path.of("../rust/target/release").resolve(libName).toAbsolutePath();
    }
}
