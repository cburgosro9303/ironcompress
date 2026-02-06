package io.ironcompress.benchmark.competitor;

public interface Compressor {
    String name();
    String algorithm();
    byte[] compress(byte[] input) throws Exception;
    byte[] decompress(byte[] input, int originalSize) throws Exception;
}
