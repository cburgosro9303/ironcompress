package io.ironcompress.benchmark.competitor;

import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class DeflateJavaCompressor implements Compressor {

    @Override
    public String name() {
        return "DEFLATE (java.util.zip)";
    }

    @Override
    public String algorithm() {
        return "DEFLATE";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        var deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        byte[] buf = new byte[input.length + 64];
        int len = deflater.deflate(buf);
        deflater.end();
        byte[] result = new byte[len];
        System.arraycopy(buf, 0, result, 0, len);
        return result;
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        var inflater = new Inflater();
        inflater.setInput(input);
        byte[] buf = new byte[originalSize];
        int len = inflater.inflate(buf);
        inflater.end();
        byte[] result = new byte[len];
        System.arraycopy(buf, 0, result, 0, len);
        return result;
    }
}
