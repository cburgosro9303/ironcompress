package io.ironcompress.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Low-level FFM bindings to the native library.
 * Returns raw {@code int} error codes — no exception translation at this layer.
 */
public final class NativeLib {

    private static final MethodHandle NATIVE_PING;
    private static final MethodHandle COMPRESS_NATIVE;
    private static final MethodHandle DECOMPRESS_NATIVE;
    private static final MethodHandle ESTIMATE_MAX_OUTPUT;

    static {
        SymbolLookup lib = NativeLoader.lookup();
        Linker linker = Linker.nativeLinker();

        NATIVE_PING = linker.downcallHandle(
                lib.findOrThrow("native_ping"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );

        // compress_native(algo: u8, level: i32, in_ptr, in_len, out_ptr, out_cap, out_len) -> i32
        COMPRESS_NATIVE = linker.downcallHandle(
                lib.findOrThrow("compress_native"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,    // return: error code
                        ValueLayout.JAVA_BYTE,   // algo (u8)
                        ValueLayout.JAVA_INT,    // level (i32)
                        ValueLayout.ADDRESS,     // in_ptr
                        ValueLayout.JAVA_LONG,   // in_len (usize)
                        ValueLayout.ADDRESS,     // out_ptr
                        ValueLayout.JAVA_LONG,   // out_cap (usize)
                        ValueLayout.ADDRESS      // out_len (*mut usize)
                )
        );

        // decompress_native(algo: u8, in_ptr, in_len, out_ptr, out_cap, out_len) -> i32
        DECOMPRESS_NATIVE = linker.downcallHandle(
                lib.findOrThrow("decompress_native"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,    // return: error code
                        ValueLayout.JAVA_BYTE,   // algo (u8)
                        ValueLayout.ADDRESS,     // in_ptr
                        ValueLayout.JAVA_LONG,   // in_len (usize)
                        ValueLayout.ADDRESS,     // out_ptr
                        ValueLayout.JAVA_LONG,   // out_cap (usize)
                        ValueLayout.ADDRESS      // out_len (*mut usize)
                )
        );

        // estimate_max_output_size_native(algo: u8, level: i32, in_len: usize) -> usize
        ESTIMATE_MAX_OUTPUT = linker.downcallHandle(
                lib.findOrThrow("estimate_max_output_size_native"),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,   // return: usize
                        ValueLayout.JAVA_BYTE,   // algo (u8)
                        ValueLayout.JAVA_INT,    // level (i32)
                        ValueLayout.JAVA_LONG    // in_len (usize)
                )
        );
    }

    private NativeLib() {}

    public static int nativePing() {
        try {
            return (int) NATIVE_PING.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("native_ping call failed", t);
        }
    }

    public static int compressNative(
            byte algo, int level,
            MemorySegment input, long inLen,
            MemorySegment output, long outCap,
            MemorySegment outLen) {
        try {
            return (int) COMPRESS_NATIVE.invokeExact(algo, level, input, inLen, output, outCap, outLen);
        } catch (Throwable t) {
            throw new RuntimeException("compress_native call failed", t);
        }
    }

    public static int decompressNative(
            byte algo,
            MemorySegment input, long inLen,
            MemorySegment output, long outCap,
            MemorySegment outLen) {
        try {
            return (int) DECOMPRESS_NATIVE.invokeExact(algo, input, inLen, output, outCap, outLen);
        } catch (Throwable t) {
            throw new RuntimeException("decompress_native call failed", t);
        }
    }

    public static long estimateMaxOutputSize(byte algo, int level, long inputLen) {
        try {
            return (long) ESTIMATE_MAX_OUTPUT.invokeExact(algo, level, inputLen);
        } catch (Throwable t) {
            throw new RuntimeException("estimate_max_output_size_native call failed", t);
        }
    }
}
