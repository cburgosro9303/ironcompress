package io.ironcompress.benchmark.competitor;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.aayushatharva.brotli4j.encoder.Encoder;

public final class BrotliJavaCompressor implements Compressor {

    static {
        Brotli4jLoader.ensureAvailability();
    }

    @Override
    public String name() {
        return "BROTLI (brotli4j)";
    }

    @Override
    public String algorithm() {
        return "BROTLI";
    }

    @Override
    public byte[] compress(byte[] input) throws Exception {
        return Encoder.compress(input);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) throws Exception {
        DirectDecompress result = Decoder.decompress(input);
        if (result.getResultStatus() != DecoderJNI.Status.DONE) {
            throw new RuntimeException("Brotli decompression failed: " + result.getResultStatus());
        }
        return result.getDecompressedData();
    }
}
