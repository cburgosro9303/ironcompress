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

    private IronCompress() {
    }

    public static byte[] compress(Algorithm algo, int level, byte[] input)
            throws IronCompressException {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }

        long estimate = estimateMaxOutputSize(algo, level, input.length);

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

    /**
     * Zero-copy compression using existing MemorySegments.
     * Use this method to avoid byte[] copy overheads.
     *
     * @param algo   Algorithm to use
     * @param level  Compression level
     * @param input  Input segment
     * @param output Output segment
     * @return Number of bytes written to output
     * @throws IronCompressException if compression fails
     */
    public static long compress(Algorithm algo, int level, MemorySegment input, MemorySegment output)
            throws IronCompressException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);
            int code = NativeLib.compressNative(
                    algo.id(), level,
                    input, input.byteSize(),
                    output, output.byteSize(),
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                return outLen.get(ValueLayout.JAVA_LONG, 0);
            }

            NativeError err = NativeError.fromCode(code);
            long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
            throw new IronCompressException(err, algo, hint);
        }
    }

    /**
     * Zero-copy decompression using existing MemorySegments.
     * Use this method to avoid byte[] copy overheads.
     *
     * @param algo   Algorithm to use
     * @param input  Input segment
     * @param output Output segment
     * @return Number of bytes written to output
     * @throws IronCompressException if decompression fails
     */
    public static long decompress(Algorithm algo, MemorySegment input, MemorySegment output)
            throws IronCompressException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);
            int code = NativeLib.decompressNative(
                    algo.id(),
                    input, input.byteSize(),
                    output, output.byteSize(),
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                return outLen.get(ValueLayout.JAVA_LONG, 0);
            }

            NativeError err = NativeError.fromCode(code);
            long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
            throw new IronCompressException(err, algo, hint);
        }
    }

    public static long estimateMaxOutputSize(Algorithm algo, int level, long inputLen) {
        // Implement estimation logic in Java to save an FFI call
        return switch (algo) {
            case LZ4 -> inputLen + (inputLen / 255) + 16;
            case SNAPPY -> 32 + inputLen + (inputLen / 6);
            case GZIP, DEFLATE -> inputLen + (inputLen / 8) + 32;
            default -> NativeLib.estimateMaxOutputSize(algo.id(), level, inputLen);
        };
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
