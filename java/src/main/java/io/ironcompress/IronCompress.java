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

    private static final System.Logger LOG = System.getLogger(IronCompress.class.getName());

    private IronCompress() {
    }

    public static byte[] compress(Algorithm algo, int level, byte[] input)
            throws IronCompressException {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }

        LOG.log(System.Logger.Level.DEBUG, "compress: algo={0}, level={1}, inputLen={2}",
                algo, level, input.length);

        long estimate = estimateMaxOutputSize(algo, level, input.length);

        byte[] result = tryCompress(algo, level, input, estimate);
        if (result != null) {
            double ratio = result.length > 0 ? (double) input.length / result.length : 0.0;
            LOG.log(System.Logger.Level.INFO,
                    "compress: algo={0}, {1} -> {2} bytes (ratio={3})",
                    algo, input.length, result.length, String.format("%.2fx", ratio));
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

        LOG.log(System.Logger.Level.DEBUG, "decompress: algo={0}, inputLen={1}, expectedSize={2}",
                algo, input.length, expectedSize);

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
                LOG.log(System.Logger.Level.INFO, "decompress: algo={0}, {1} -> {2} bytes",
                        algo, input.length, n);
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
        LOG.log(System.Logger.Level.DEBUG,
                "compress(segment): algo={0}, level={1}, inputSize={2}, outputCap={3}",
                algo, level, input.byteSize(), output.byteSize());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);
            int code = NativeLib.compressNative(
                    algo.id(), level,
                    input, input.byteSize(),
                    output, output.byteSize(),
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                long n = outLen.get(ValueLayout.JAVA_LONG, 0);
                LOG.log(System.Logger.Level.INFO,
                        "compress(segment): algo={0}, {1} -> {2} bytes",
                        algo, input.byteSize(), n);
                return n;
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
        LOG.log(System.Logger.Level.DEBUG,
                "decompress(segment): algo={0}, inputSize={1}, outputCap={2}",
                algo, input.byteSize(), output.byteSize());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);
            int code = NativeLib.decompressNative(
                    algo.id(),
                    input, input.byteSize(),
                    output, output.byteSize(),
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                long n = outLen.get(ValueLayout.JAVA_LONG, 0);
                LOG.log(System.Logger.Level.INFO,
                        "decompress(segment): algo={0}, {1} -> {2} bytes",
                        algo, input.byteSize(), n);
                return n;
            }

            NativeError err = NativeError.fromCode(code);
            long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
            throw new IronCompressException(err, algo, hint);
        }
    }

    public static long estimateMaxOutputSize(Algorithm algo, int level, long inputLen) {
        long estimate = switch (algo) {
            case LZ4 -> inputLen + (inputLen / 255) + 16;
            case SNAPPY -> 32 + inputLen + (inputLen / 6);
            case GZIP, DEFLATE -> inputLen + (inputLen / 8) + 32;
            default -> NativeLib.estimateMaxOutputSize(algo.id(), level, inputLen);
        };
        LOG.log(System.Logger.Level.TRACE,
                "estimateMaxOutputSize: algo={0}, inputLen={1}, estimate={2}",
                algo, inputLen, estimate);
        return estimate;
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
                    long oldSize = bufferSize;
                    bufferSize = (hint > bufferSize) ? hint : bufferSize * 2;
                    LOG.log(System.Logger.Level.DEBUG,
                            "tryCompress: buffer too small, resizing {0} -> {1} (hint={2})",
                            oldSize, bufferSize, hint);
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
