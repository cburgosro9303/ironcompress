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

        // Copy input to off-heap
        MemorySegment.copy(input, 0, inputBuffer, ValueLayout.JAVA_BYTE, 0, input.length);

        int code = NativeLib.compressNative(
                algo.id(), level,
                inputBuffer, input.length,
                outputBuffer, outputBuffer.byteSize(),
                outLen);

        if (code == NativeError.SUCCESS.code()) {
            long n = outLen.get(ValueLayout.JAVA_LONG, 0);
            return outputBuffer.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
        }

        // Handle buffer too small (resize and retry once)
        if (code == NativeError.BUFFER_TOO_SMALL.code()) {
            long needed = outLen.get(ValueLayout.JAVA_LONG, 0);
            ensureOutputCapacity(needed);

            code = NativeLib.compressNative(
                    algo.id(), level,
                    inputBuffer, input.length,
                    outputBuffer, outputBuffer.byteSize(),
                    outLen);

            if (code == NativeError.SUCCESS.code()) {
                long n = outLen.get(ValueLayout.JAVA_LONG, 0);
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

        MemorySegment.copy(input, 0, inputBuffer, ValueLayout.JAVA_BYTE, 0, input.length);

        int code = NativeLib.decompressNative(
                algo.id(),
                inputBuffer, input.length,
                outputBuffer, outputBuffer.byteSize(),
                outLen);

        if (code == NativeError.SUCCESS.code()) {
            long n = outLen.get(ValueLayout.JAVA_LONG, 0);
            return outputBuffer.asSlice(0, n).toArray(ValueLayout.JAVA_BYTE);
        }

        NativeError err = NativeError.fromCode(code);
        long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
        throw new IronCompressException(err, algo, hint);
    }

    private void ensureInputCapacity(long size) {
        if (inputBuffer.byteSize() < size) {
            // Allocate new larger buffer in existing arena (old one is leaked until close)
            // If growth is excessive, we might want to recycle the Arena, but that's
            // expensive.
            // For now, simple reallocation.
            inputBuffer = arena.allocate(Math.max(size, inputBuffer.byteSize() * 2));
        }
    }

    private void ensureOutputCapacity(long size) {
        if (outputBuffer.byteSize() < size) {
            outputBuffer = arena.allocate(Math.max(size, outputBuffer.byteSize() * 2));
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
        }
    }
}
