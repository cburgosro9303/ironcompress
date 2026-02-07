package io.ironcompress;

import io.ironcompress.ffi.NativeLib;
import io.ironcompress.model.Algorithm;
import io.ironcompress.model.IronCompressException;
import io.ironcompress.model.NativeError;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Stateful compressor that reuses buffers to minimize allocation overhead.
 * <p>
 * This class is not thread-safe. Use thread-local instances or synchronization
 * if needed.
 * Best suited for scenarios where the input size is relatively stable or
 * bounded.
 * </p>
 */
public final class IronCompressor implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(IronCompressor.class.getName());

    private Arena arena;
    private MemorySegment inputBuffer;
    private MemorySegment outputBuffer;
    private MemorySegment outLen;
    private boolean closed = false;

    public IronCompressor() {
        init(4096, 4096);
    }

    public IronCompressor(long initialInputSize, long initialOutputSize) {
        init(initialInputSize, initialOutputSize);
    }

    private void init(long inSize, long outSize) {
        this.arena = Arena.ofConfined();
        this.inputBuffer = this.arena.allocate(inSize);
        this.outputBuffer = this.arena.allocate(outSize);
        this.outLen = this.arena.allocate(ValueLayout.JAVA_LONG);
        LOG.log(System.Logger.Level.INFO,
                "IronCompressor created: inputBuffer={0}, outputBuffer={1}",
                inSize, outSize);
    }

    /**
     * Compress the input array into a new byte array.
     * Reuses internal buffers to avoid off-heap allocation.
     */
    public byte[] compress(Algorithm algo, int level, byte[] input) throws IronCompressException {
        ensureOpen();
        ensureInputCapacity(input.length);

        long maxOut = IronCompress.estimateMaxOutputSize(algo, level, input.length);
        ensureOutputCapacity(maxOut);

        LOG.log(System.Logger.Level.DEBUG,
                "compress: algo={0}, level={1}, inputLen={2}, outputCap={3}",
                algo, level, input.length, outputBuffer.byteSize());

        // Copy input to off-heap
        MemorySegment.copy(input, 0, inputBuffer, ValueLayout.JAVA_BYTE, 0, input.length);

        int code = NativeLib.compressNative(
                algo.id(), level,
                inputBuffer, input.length,
                outputBuffer, outputBuffer.byteSize(),
                outLen);

        if (code == NativeError.SUCCESS.code()) {
            long n = outLen.get(ValueLayout.JAVA_LONG, 0);
            LOG.log(System.Logger.Level.INFO,
                    "compress: algo={0}, {1} -> {2} bytes",
                    algo, input.length, n);
            return outputBuffer.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
        }

        // Handle buffer too small (resize and retry once)
        if (code == NativeError.BUFFER_TOO_SMALL.code()) {
            long needed = outLen.get(ValueLayout.JAVA_LONG, 0);
            LOG.log(System.Logger.Level.DEBUG,
                    "compress: buffer too small, resizing to {0}", needed);
            ensureOutputCapacity(needed);

            code = NativeLib.compressNative(
                    algo.id(), level,
                    inputBuffer, input.length,
                    outputBuffer, outputBuffer.byteSize(),
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                long n = outLen.get(ValueLayout.JAVA_LONG, 0);
                LOG.log(System.Logger.Level.INFO,
                        "compress: algo={0}, {1} -> {2} bytes (after retry)",
                        algo, input.length, n);
                return outputBuffer.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
            }
        }

        NativeError err = NativeError.fromCode(code);
        long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
        throw new IronCompressException(err, algo, hint);
    }

    /**
     * Decompress the input array.
     */
    public byte[] decompress(Algorithm algo, byte[] input, int expectedSize) throws IronCompressException {
        ensureOpen();
        ensureInputCapacity(input.length);
        ensureOutputCapacity(expectedSize);

        LOG.log(System.Logger.Level.DEBUG,
                "decompress: algo={0}, inputLen={1}, expectedSize={2}",
                algo, input.length, expectedSize);

        MemorySegment.copy(input, 0, inputBuffer, ValueLayout.JAVA_BYTE, 0, input.length);

        int code = NativeLib.decompressNative(
                algo.id(),
                inputBuffer, input.length,
                outputBuffer, outputBuffer.byteSize(),
                outLen);

        if (code == NativeError.SUCCESS.code()) {
            long n = outLen.get(ValueLayout.JAVA_LONG, 0);
            LOG.log(System.Logger.Level.INFO,
                    "decompress: algo={0}, {1} -> {2} bytes",
                    algo, input.length, n);
            return outputBuffer.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
        }

        NativeError err = NativeError.fromCode(code);
        long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
        throw new IronCompressException(err, algo, hint);
    }

    private void ensureInputCapacity(long size) {
        if (inputBuffer.byteSize() < size) {
            long newSize = Math.max(size, inputBuffer.byteSize() * 2);
            LOG.log(System.Logger.Level.DEBUG,
                    "Growing input buffer: {0} -> {1}", inputBuffer.byteSize(), newSize);
            inputBuffer = arena.allocate(newSize);
        }
    }

    private void ensureOutputCapacity(long size) {
        if (outputBuffer.byteSize() < size) {
            long newSize = Math.max(size, outputBuffer.byteSize() * 2);
            LOG.log(System.Logger.Level.DEBUG,
                    "Growing output buffer: {0} -> {1}", outputBuffer.byteSize(), newSize);
            outputBuffer = arena.allocate(newSize);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("IronCompressor is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            arena.close();
            closed = true;
            LOG.log(System.Logger.Level.INFO, "IronCompressor closed");
        }
    }
}
