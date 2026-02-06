package io.ironcompress;

import io.ironcompress.model.Algorithm;
import io.ironcompress.model.IronCompressException;
import io.ironcompress.model.NativeError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IronCompressTest {

    private static final byte[] TEST_DATA =
            "Hello world! ".repeat(100).getBytes(StandardCharsets.UTF_8);

    // --- compress success tests ---

    @Test
    void compressLz4Success() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.LZ4, -1, TEST_DATA);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < TEST_DATA.length);
    }

    @Test
    void compressSnappySuccess() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.SNAPPY, -1, TEST_DATA);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < TEST_DATA.length);
    }

    @Test
    void compressGzipSuccess() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.GZIP, 6, TEST_DATA);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < TEST_DATA.length);
    }

    @Test
    void compressDeflateSuccess() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.DEFLATE, 6, TEST_DATA);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < TEST_DATA.length);
    }

    @Test
    void compressBrotliSuccess() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.BROTLI, 6, TEST_DATA);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < TEST_DATA.length);
    }

    @Test
    void compressZstdSuccess() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.ZSTD, 3, TEST_DATA);
        assertTrue(compressed.length > 0);
        assertTrue(compressed.length < TEST_DATA.length);
    }

    // --- error cases ---

    @Test
    void compressNullInputThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> IronCompress.compress(Algorithm.LZ4, -1, null));
    }

    @Test
    void decompressInvalidDataThrows() {
        IronCompressException ex = assertThrows(IronCompressException.class,
                () -> IronCompress.decompress(Algorithm.LZ4, new byte[]{1, 2, 3}, 100));
        assertEquals(NativeError.INTERNAL_ERROR, ex.error());
    }

    // --- estimate ---

    @Test
    void estimateReturnsPositive() {
        long estimate = IronCompress.estimateMaxOutputSize(Algorithm.LZ4, -1, 1000);
        assertTrue(estimate > 0);
    }

    // --- exception fields ---

    @Test
    void exceptionContainsAlgorithm() {
        IronCompressException ex = assertThrows(IronCompressException.class,
                () -> IronCompress.decompress(Algorithm.GZIP, new byte[]{1, 2, 3}, 100));
        assertEquals(NativeError.INTERNAL_ERROR, ex.error());
        assertEquals(Algorithm.GZIP, ex.algorithm());
    }

    // --- individual roundtrip tests ---

    @Test
    void roundtripLz4() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.LZ4, -1, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.LZ4, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripSnappy() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.SNAPPY, -1, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.SNAPPY, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripGzip() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.GZIP, 6, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.GZIP, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripDeflate() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.DEFLATE, 6, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.DEFLATE, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripZstd() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.ZSTD, 3, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.ZSTD, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripBrotli() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.BROTLI, 6, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.BROTLI, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripLzma2() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.LZMA2, 6, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.LZMA2, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripBzip2() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.BZIP2, 6, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.BZIP2, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    @Test
    void roundtripLzf() throws IronCompressException {
        byte[] compressed = IronCompress.compress(Algorithm.LZF, 0, TEST_DATA);
        byte[] decompressed = IronCompress.decompress(Algorithm.LZF, compressed, TEST_DATA.length);
        assertArrayEquals(TEST_DATA, decompressed);
    }

    // --- FASE 10: Parameterized roundtrip test for all 9 algorithms ---

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void roundtripAllAlgorithms(Algorithm algo) throws IronCompressException {
        byte[] input = "Parameterized roundtrip test data! ".repeat(50).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = IronCompress.compress(algo, -1, input);
        assertTrue(compressed.length > 0, algo + " compressed size should be > 0");
        byte[] decompressed = IronCompress.decompress(algo, compressed, input.length);
        assertArrayEquals(input, decompressed, algo + " roundtrip failed");
    }

    // --- FASE 10: Edge case tests ---

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void roundtripSingleByte(Algorithm algo) throws IronCompressException {
        byte[] input = new byte[]{42};
        byte[] compressed = IronCompress.compress(algo, -1, input);
        assertTrue(compressed.length > 0);
        byte[] decompressed = IronCompress.decompress(algo, compressed, input.length);
        assertArrayEquals(input, decompressed, algo + " single byte roundtrip failed");
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void roundtripLargeInput(Algorithm algo) throws IronCompressException {
        // 1 MB of compressible data
        byte[] input = "ABCDEFGHIJ".repeat(100_000).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = IronCompress.compress(algo, -1, input);
        assertTrue(compressed.length > 0);
        byte[] decompressed = IronCompress.decompress(algo, compressed, input.length);
        assertArrayEquals(input, decompressed, algo + " large input roundtrip failed");
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void roundtripRandomBytes(Algorithm algo) throws IronCompressException {
        // Incompressible random data
        byte[] input = new byte[1024];
        new Random(42).nextBytes(input);
        byte[] compressed = IronCompress.compress(algo, -1, input);
        assertTrue(compressed.length > 0);
        byte[] decompressed = IronCompress.decompress(algo, compressed, input.length);
        assertArrayEquals(input, decompressed, algo + " random bytes roundtrip failed");
    }
}
