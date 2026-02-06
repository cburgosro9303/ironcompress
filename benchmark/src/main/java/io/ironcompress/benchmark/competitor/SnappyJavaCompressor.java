package io.ironcompress.benchmark.competitor;

import org.xerial.snappy.Snappy;

public final class SnappyJavaCompressor implements Compressor {

    @Override
    public String name() {
        return "SNAPPY (snappy-java)";
    }

    @Override
    public String algorithm() {
        return "SNAPPY";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        return Snappy.compress(input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        return Snappy.uncompress(input);
    }
}
