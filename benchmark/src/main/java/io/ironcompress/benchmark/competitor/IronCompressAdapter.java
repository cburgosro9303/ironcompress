package io.ironcompress.benchmark.competitor;

import io.ironcompress.IronCompress;
import io.ironcompress.model.Algorithm;

public final class IronCompressAdapter implements Compressor {

    private final Algorithm algo;

    public IronCompressAdapter(Algorithm algo) {
        this.algo = algo;
    }

    @Override
    public String name() {
        return algo.name() + " (IronCompress)";
    }

    @Override
    public String algorithm() {
        return algo.name();
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        return IronCompress.compress(algo, 0, input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        return IronCompress.decompress(algo, input, originalSize);
    }
}
