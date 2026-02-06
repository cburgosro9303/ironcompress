package io.ironcompress.benchmark.competitor;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class Bzip2JavaCompressor implements Compressor {

    @Override
    public String name() {
        return "BZIP2 (commons-compress)";
    }

    @Override
    public String algorithm() {
        return "BZIP2";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        var baos = new ByteArrayOutputStream(input.length);
        try (var bz2 = new BZip2CompressorOutputStream(baos)) {
            bz2.write(input);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        var bais = new ByteArrayInputStream(input);
        try (var bz2 = new BZip2CompressorInputStream(bais)) {
            return bz2.readAllBytes();
        }
    }
}
