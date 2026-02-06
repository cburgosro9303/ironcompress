package io.ironcompress.benchmark.competitor;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class Lzma2JavaCompressor implements Compressor {

    @Override
    public String name() {
        return "LZMA2 (xz-java)";
    }

    @Override
    public String algorithm() {
        return "LZMA2";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        var baos = new ByteArrayOutputStream(input.length);
        try (var xz = new XZOutputStream(baos, new LZMA2Options())) {
            xz.write(input);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        var bais = new ByteArrayInputStream(input);
        try (var xz = new XZInputStream(bais)) {
            return xz.readAllBytes();
        }
    }
}
