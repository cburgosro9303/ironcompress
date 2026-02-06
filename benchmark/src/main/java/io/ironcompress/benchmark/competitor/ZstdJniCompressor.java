package io.ironcompress.benchmark.competitor;

import com.github.luben.zstd.Zstd;

public final class ZstdJniCompressor implements Compressor {

    @Override
    public String name() {
        return "ZSTD (zstd-jni)";
    }

    @Override
    public String algorithm() {
        return "ZSTD";
    }

    @Override
    public byte[] compress(byte[] input) {
        return Zstd.compress(input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) {
        return Zstd.decompress(input, originalSize);
    }
}
