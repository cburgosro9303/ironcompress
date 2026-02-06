package io.ironcompress.benchmark.competitor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipJavaCompressor implements Compressor {

    @Override
    public String name() {
        return "GZIP (java.util.zip)";
    }

    @Override
    public String algorithm() {
        return "GZIP";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        var baos = new ByteArrayOutputStream(input.length);
        try (var gzip = new GZIPOutputStream(baos)) {
            gzip.write(input);
        }
        return baos.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        var bais = new ByteArrayInputStream(input);
        try (var gzip = new GZIPInputStream(bais)) {
            return gzip.readAllBytes();
        }
    }
}
