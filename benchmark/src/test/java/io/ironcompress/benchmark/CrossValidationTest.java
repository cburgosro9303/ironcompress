package io.ironcompress.benchmark;

import io.ironcompress.benchmark.competitor.Compressor;
import io.ironcompress.benchmark.competitor.CompressorFactory;
import io.ironcompress.benchmark.data.TestDataGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CrossValidationTest {

    // Algorithms where cross-decompression is NOT compatible due to framing differences:
    // LZ4: lz4_flex vs lz4-java differ in block format
    // SNAPPY: snappy crate vs snappy-java differ in framing
    // LZF: IronCompress uses custom flag prefix (0x00=raw, 0x01=compressed)
    // DEFLATE: Rust flate2 uses raw deflate, Java Deflater uses zlib-wrapped format
    private static final Set<String> CROSS_EXCLUDED = Set.of("LZ4", "SNAPPY", "LZF", "DEFLATE");

    static Stream<Arguments> ironRoundtripArgs() {
        return buildArgs().stream();
    }

    static Stream<Arguments> javaRoundtripArgs() {
        return buildArgs().stream();
    }

    static Stream<Arguments> crossDecompressionArgs() {
        return buildArgs().stream()
                .filter(a -> !CROSS_EXCLUDED.contains(((CompressorFactory.Pair) a.get()[0]).algorithm()));
    }

    @ParameterizedTest(name = "Iron roundtrip: {1} - {2}")
    @MethodSource("ironRoundtripArgs")
    void ironCompressRoundtrip(CompressorFactory.Pair pair, String algo, String dataLabel, byte[] data)
            throws Exception {
        Compressor iron = pair.iron();
        byte[] compressed = iron.compress(data);
        byte[] decompressed = iron.decompress(compressed, data.length);
        assertArrayEquals(data, decompressed,
                "IronCompress roundtrip failed for " + algo + " with " + dataLabel);
    }

    @ParameterizedTest(name = "Java roundtrip: {1} - {2}")
    @MethodSource("javaRoundtripArgs")
    void javaCompressorRoundtrip(CompressorFactory.Pair pair, String algo, String dataLabel, byte[] data)
            throws Exception {
        Compressor java = pair.java();
        byte[] compressed = java.compress(data);
        byte[] decompressed = java.decompress(compressed, data.length);
        assertArrayEquals(data, decompressed,
                "Java roundtrip failed for " + algo + " with " + dataLabel);
    }

    @ParameterizedTest(name = "Cross Iron→Java: {1} - {2}")
    @MethodSource("crossDecompressionArgs")
    void crossDecompressionIronToJava(CompressorFactory.Pair pair, String algo, String dataLabel, byte[] data)
            throws Exception {
        byte[] compressed = pair.iron().compress(data);
        byte[] decompressed = pair.java().decompress(compressed, data.length);
        assertArrayEquals(data, decompressed,
                "Cross Iron→Java failed for " + algo + " with " + dataLabel);
    }

    @ParameterizedTest(name = "Cross Java→Iron: {1} - {2}")
    @MethodSource("crossDecompressionArgs")
    void crossDecompressionJavaToIron(CompressorFactory.Pair pair, String algo, String dataLabel, byte[] data)
            throws Exception {
        byte[] compressed = pair.java().compress(data);
        byte[] decompressed = pair.iron().decompress(compressed, data.length);
        assertArrayEquals(data, decompressed,
                "Cross Java→Iron failed for " + algo + " with " + dataLabel);
    }

    private static List<Arguments> buildArgs() {
        var pairs = CompressorFactory.allPairs();
        var dataSets = List.of(
                new Object[]{"1KB text", TestDataGenerator.compressibleText(1024)},
                new Object[]{"100KB text", TestDataGenerator.compressibleText(100 * 1024)},
                new Object[]{"1KB random", TestDataGenerator.randomBytes(1024)}
        );

        var args = new ArrayList<Arguments>();
        for (var pair : pairs) {
            for (var ds : dataSets) {
                args.add(Arguments.of(pair, pair.algorithm(), ds[0], ds[1]));
            }
        }
        return args;
    }
}
