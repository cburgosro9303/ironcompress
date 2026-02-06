package io.ironcompress.benchmark.report;

public record BenchmarkResult(
        String name,
        String algorithm,
        int originalSize,
        int compressedSize,
        double compressMs,
        double decompressMs
) {
    public double ratio() {
        return compressedSize == 0 ? 0 : (double) originalSize / compressedSize;
    }
}
