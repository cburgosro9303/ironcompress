package io.ironcompress.benchmark.report;

import io.ironcompress.benchmark.competitor.Compressor;
import io.ironcompress.benchmark.competitor.CompressorFactory;
import io.ironcompress.benchmark.data.TestDataGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComparisonRunner {

    private static final int DEFAULT_WARMUP = 50;
    private static final int DEFAULT_ITERATIONS = 200;

    public static void main(String[] args) throws Exception {
        var dataSets = new ArrayList<DataSet>();

        // Check for external file (benchmark.file system property)
        String filePath = System.getProperty("benchmark.file");
        if (filePath != null) {
            dataSets.add(loadFromFile(filePath));
        }

        // Check for custom size (benchmark.size system property, e.g. "1GB", "500MB")
        String sizeStr = System.getProperty("benchmark.size");
        if (sizeStr != null) {
            int size = parseSize(sizeStr);
            dataSets.add(new DataSet(formatSize(size) + " - Generated Text", TestDataGenerator.compressibleText(size)));
        }

        // If no custom input, use default data sets
        if (dataSets.isEmpty()) {
            dataSets.addAll(List.of(
                    new DataSet("1 KB - Compressible Text", TestDataGenerator.compressibleText(1024)),
                    new DataSet("100 KB - Compressible Text", TestDataGenerator.compressibleText(100 * 1024)),
                    new DataSet("1 MB - Compressible Text", TestDataGenerator.compressibleText(1024 * 1024)),
                    new DataSet("1 KB - Random Bytes", TestDataGenerator.randomBytes(1024)),
                    new DataSet("100 KB - Random Bytes", TestDataGenerator.randomBytes(100 * 1024))
            ));
        }

        var pairs = CompressorFactory.allPairs();

        for (var ds : dataSets) {
            int warmup = scaleIterations(DEFAULT_WARMUP, ds.data().length);
            int iterations = scaleIterations(DEFAULT_ITERATIONS, ds.data().length);

            printHeader(ds.label(), ds.data().length, warmup, iterations);

            for (var pair : pairs) {
                var results = new ArrayList<BenchmarkResult>();

                var ironResult = benchmark(pair.iron(), ds.data(), warmup, iterations);
                if (ironResult != null) results.add(ironResult);

                var javaResult = benchmark(pair.java(), ds.data(), warmup, iterations);
                if (javaResult != null) results.add(javaResult);

                for (var r : results) {
                    printRow(r);
                }
                System.out.println("  " + "-".repeat(72));
            }
            System.out.println();
        }
    }

    private static BenchmarkResult benchmark(Compressor compressor, byte[] data, int warmup, int iterations) {
        try {
            // Warmup
            for (int i = 0; i < warmup; i++) {
                byte[] compressed = compressor.compress(data);
                compressor.decompress(compressed, data.length);
            }

            // Measure compression
            byte[] compressed = null;
            long compStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                compressed = compressor.compress(data);
            }
            long compEnd = System.nanoTime();
            double compMs = (compEnd - compStart) / (iterations * 1_000_000.0);

            // Measure decompression
            long decStart = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                compressor.decompress(compressed, data.length);
            }
            long decEnd = System.nanoTime();
            double decMs = (decEnd - decStart) / (iterations * 1_000_000.0);

            return new BenchmarkResult(
                    compressor.name(),
                    compressor.algorithm(),
                    data.length,
                    compressed.length,
                    compMs,
                    decMs
            );
        } catch (Exception e) {
            System.err.println("  SKIP " + compressor.name() + ": " + e.getMessage());
            return null;
        }
    }

    private static DataSet loadFromFile(String path) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(path));
        String name = Path.of(path).getFileName().toString();
        return new DataSet(formatSize(data.length) + " - File: " + name, data);
    }

    static int parseSize(String s) {
        s = s.trim().toUpperCase();
        long multiplier = 1;
        if (s.endsWith("GB")) {
            multiplier = 1024L * 1024 * 1024;
            s = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("MB")) {
            multiplier = 1024L * 1024;
            s = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("KB")) {
            multiplier = 1024L;
            s = s.substring(0, s.length() - 2).trim();
        }
        long size = (long) (Double.parseDouble(s) * multiplier);
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Size too large (max 2GB): " + size);
        }
        return (int) size;
    }

    private static String formatSize(int bytes) {
        if (bytes >= 1024 * 1024 * 1024) return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes >= 1024) return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private static int scaleIterations(int base, int dataSize) {
        // Reduce iterations for large data to keep runtime reasonable
        if (dataSize >= 1024 * 1024 * 100) return Math.max(base / 50, 2);  // >=100MB: 2-4 iters
        if (dataSize >= 1024 * 1024 * 10)  return Math.max(base / 20, 3);  // >=10MB: 3-10 iters
        if (dataSize >= 1024 * 1024)        return Math.max(base / 5, 5);   // >=1MB: 10-40 iters
        return base;
    }

    private static void printHeader(String label, int size, int warmup, int iterations) {
        System.out.println("=".repeat(78));
        System.out.printf("  Data: %s (%,d bytes) [warmup=%d, iter=%d]%n", label, size, warmup, iterations);
        System.out.println("=".repeat(78));
        System.out.printf("%-32s %12s %8s %10s %10s%n",
                "Implementation", "Compressed", "Ratio", "Comp(ms)", "Decomp(ms)");
        System.out.println("-".repeat(78));
    }

    private static void printRow(BenchmarkResult r) {
        System.out.printf(Locale.US, "%-32s %,12d %7.2fx %10.3f %10.3f%n",
                r.name(),
                r.compressedSize(),
                r.ratio(),
                r.compressMs(),
                r.decompressMs());
    }

    private record DataSet(String label, byte[] data) {}
}
