package io.ironcompress.benchmark.competitor;

import com.ning.compress.lzf.LZFDecoder;
import com.ning.compress.lzf.LZFEncoder;

public final class LzfJavaCompressor implements Compressor {

    @Override
    public String name() {
        return "LZF (compress-lzf)";
    }

    @Override
    public String algorithm() {
        return "LZF";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        return LZFEncoder.encode(input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        return LZFDecoder.decode(input);
    }
}
