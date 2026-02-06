package io.ironcompress;

import io.ironcompress.ffi.NativeLib;
import io.ironcompress.model.Algorithm;
import io.ironcompress.model.IronCompressException;
import io.ironcompress.model.NativeError;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * High-level compression API. Receives {@code byte[]}, returns {@code byte[]},
 * throws {@link IronCompressException} on failure.
 */
public final class IronCompress {

    private IronCompress() {}

    public static byte[] compress(Algorithm algo, int level, byte[] input)
            throws IronCompressException {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }

        long estimate = NativeLib.estimateMaxOutputSize(algo.id(), level, input.length);
        if (estimate <= 0) {
            estimate = (long) input.length * 2 + 64;
        }

        byte[] result = tryCompress(algo, level, input, estimate);
        if (result != null) {
            return result;
        }

        // BUFFER_TOO_SMALL was handled inside tryCompress — should not reach here
        throw new IronCompressException(NativeError.INTERNAL_ERROR, algo);
    }

    public static byte[] decompress(Algorithm algo, byte[] input, int expectedSize)
            throws IronCompressException {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (expectedSize <= 0) {
            throw new IllegalArgumentException("expectedSize must be > 0");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocate(input.length);
            inSeg.copyFrom(MemorySegment.ofArray(input));

            MemorySegment outSeg = arena.allocate(expectedSize);
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);

            int code = NativeLib.decompressNative(
                    algo.id(),
                    inSeg, input.length,
                    outSeg, expectedSize,
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                int n = (int) outLen.get(ValueLayout.JAVA_LONG, 0);
                return outSeg.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
            }

            NativeError err = NativeError.fromCode(code);
            long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
            throw new IronCompressException(err, algo, hint);
        }
    }

    public static long estimateMaxOutputSize(Algorithm algo, int level, long inputLen) {
        return NativeLib.estimateMaxOutputSize(algo.id(), level, inputLen);
    }

    /**
     * Attempts compression; on BUFFER_TOO_SMALL retries once with the hint size.
     * Returns the compressed bytes on success, or throws on non-recoverable error.
     */
    private static byte[] tryCompress(Algorithm algo, int level, byte[] input, long bufferSize)
            throws IronCompressException {
        for (int attempt = 0; attempt < 2; attempt++) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSeg = arena.allocate(input.length);
                inSeg.copyFrom(MemorySegment.ofArray(input));

                MemorySegment outSeg = arena.allocate(bufferSize);
                MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);

                int code = NativeLib.compressNative(
                        algo.id(), level,
                        inSeg, input.length,
                        outSeg, bufferSize,
                        outLen);

                if (code == NativeError.SUCCESS.code()) {
                    int n = (int) outLen.get(ValueLayout.JAVA_LONG, 0);
                    return outSeg.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
                }

                if (code == NativeError.BUFFER_TOO_SMALL.code() && attempt == 0) {
                    long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
                    bufferSize = (hint > bufferSize) ? hint : bufferSize * 2;
                    continue;
                }

                NativeError err = NativeError.fromCode(code);
                long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
                throw new IronCompressException(err, algo, hint);
            }
        }
        return null;
    }
}
