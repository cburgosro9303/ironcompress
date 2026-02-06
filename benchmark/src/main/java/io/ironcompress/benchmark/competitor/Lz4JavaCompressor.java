package io.ironcompress.benchmark.competitor;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

public final class Lz4JavaCompressor implements Compressor {

    private final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    private final LZ4FastDecompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();

    @Override
    public String name() {
        return "LZ4 (lz4-java)";
    }

    @Override
    public String algorithm() {
        return "LZ4";
    }

    @Override
    public byte[] compress(byte[] input) {
        return compressor.compress(input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) {
        return decompressor.decompress(input, originalSize);
    }
}
