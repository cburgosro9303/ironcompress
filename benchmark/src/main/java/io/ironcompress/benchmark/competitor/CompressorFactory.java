package io.ironcompress.benchmark.competitor;

import io.ironcompress.model.Algorithm;

import java.util.ArrayList;
import java.util.List;

public final class CompressorFactory {

    public record Pair(Compressor iron, Compressor java) {
        public String algorithm() {
            return iron.algorithm();
        }
    }

    private CompressorFactory() {
    }

    public static List<Pair> allPairs() {
        var pairs = new ArrayList<Pair>();
        pairs.add(new Pair(new IronCompressAdapter(Algorithm.LZ4), new Lz4JavaCompressor()));
        pairs.add(new Pair(new IronCompressZeroCopyAdapter(Algorithm.LZ4), new Lz4JavaCompressor()));

        pairs.add(new Pair(new IronCompressAdapter(Algorithm.SNAPPY), new SnappyJavaCompressor()));
        pairs.add(new Pair(new IronCompressZeroCopyAdapter(Algorithm.SNAPPY), new SnappyJavaCompressor()));

        pairs.add(new Pair(new IronCompressAdapter(Algorithm.ZSTD), new ZstdJniCompressor()));
        pairs.add(new Pair(new IronCompressZeroCopyAdapter(Algorithm.ZSTD), new ZstdJniCompressor()));

        pairs.add(new Pair(new IronCompressAdapter(Algorithm.GZIP), new GzipJavaCompressor()));
        pairs.add(new Pair(new IronCompressZeroCopyAdapter(Algorithm.GZIP), new GzipJavaCompressor()));

        pairs.add(new Pair(new IronCompressAdapter(Algorithm.BROTLI), new BrotliJavaCompressor()));
        pairs.add(new Pair(new IronCompressZeroCopyAdapter(Algorithm.BROTLI), new BrotliJavaCompressor()));

        pairs.add(new Pair(new IronCompressAdapter(Algorithm.LZMA2), new Lzma2JavaCompressor()));
        // Zero-copy for others too if desired, but prioritize key ones

        pairs.add(new Pair(new IronCompressAdapter(Algorithm.BZIP2), new Bzip2JavaCompressor()));
        pairs.add(new Pair(new IronCompressAdapter(Algorithm.LZF), new LzfJavaCompressor()));
        pairs.add(new Pair(new IronCompressAdapter(Algorithm.DEFLATE), new DeflateJavaCompressor()));
        pairs.add(new Pair(new IronCompressZeroCopyAdapter(Algorithm.DEFLATE), new DeflateJavaCompressor()));

        return pairs;
    }
}
