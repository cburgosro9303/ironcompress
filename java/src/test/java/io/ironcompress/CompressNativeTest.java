package io.ironcompress;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CompressNativeTest {

    private static final byte LZ4 = 1;

    @Test
    void lz4CompressSuccess() {
        byte[] input = "Hello world! ".repeat(100).getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocate(input.length);
            inSeg.copyFrom(MemorySegment.ofArray(input));

            long estimate = NativeLib.estimateMaxOutputSize(LZ4, -1, input.length);
            assertTrue(estimate > 0, "estimate should be > 0");

            MemorySegment outSeg = arena.allocate(estimate);
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);

            int result = NativeLib.compressNative(
                    LZ4, -1,
                    inSeg, input.length,
                    outSeg, estimate,
                    outLen);

            assertEquals(0, result, "compress should return SUCCESS (0)");

            long compressedSize = outLen.get(ValueLayout.JAVA_LONG, 0);
            assertTrue(compressedSize > 0, "compressed size should be > 0");
            assertTrue(compressedSize < input.length, "compressed should be smaller than input");
        }
    }

    @Test
    void bufferTooSmallReturnsHint() {
        byte[] input = "Hello world! ".repeat(100).getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocate(input.length);
            inSeg.copyFrom(MemorySegment.ofArray(input));

            MemorySegment outSeg = arena.allocate(4); // too small
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);

            int result = NativeLib.compressNative(
                    LZ4, -1,
                    inSeg, input.length,
                    outSeg, 4,
                    outLen);

            assertEquals(-1, result, "should return BUFFER_TOO_SMALL (-1)");

            long hint = outLen.get(ValueLayout.JAVA_LONG, 0);
            assertTrue(hint > 4, "hint should be larger than the tiny buffer");
        }
    }

    @Test
    void unknownAlgoReturnsAlgoNotFound() {
        byte[] input = "test".getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocate(input.length);
            inSeg.copyFrom(MemorySegment.ofArray(input));

            MemorySegment outSeg = arena.allocate(256);
            MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);

            int result = NativeLib.compressNative(
                    (byte) 255, -1,
                    inSeg, input.length,
                    outSeg, 256,
                    outLen);

            assertEquals(-2, result, "should return ALGO_NOT_FOUND (-2)");
        }
    }

    @Test
    void estimateLz4ReturnsPositive() {
        long estimate = NativeLib.estimateMaxOutputSize(LZ4, -1, 1000);
        assertTrue(estimate > 0, "LZ4 estimate for 1000 bytes should be > 0");
    }
}
