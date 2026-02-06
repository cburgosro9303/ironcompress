package io.ironcompress.benchmark.competitor;

import io.ironcompress.IronCompressor;
import io.ironcompress.model.Algorithm;

public final class IronCompressZeroCopyAdapter implements Compressor, AutoCloseable {

    private final Algorithm algo;
    private final IronCompressor compressor;

    public IronCompressZeroCopyAdapter(Algorithm algo) {
        this.algo = algo;
        this.compressor = new IronCompressor();
    }

    @Override
    public String name() {
        return algo.name() + " (IronCompress Zero-Copy)";
    }

    @Override
    public String algorithm() {
        return algo.name();
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        return compressor.compress(algo, 0, input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        return compressor.decompress(algo, input, originalSize);
    }

    @Override
    public void close() throws Exception {
        compressor.close();
    }
}
