package io.ironcompress.benchmark.data;

import java.util.Random;

public final class TestDataGenerator {

    private TestDataGenerator() {}

    public static byte[] compressibleText(int size) {
        var sb = new StringBuilder(size);
        String phrase = "The quick brown fox jumps over the lazy dog. ";
        while (sb.length() < size) {
            sb.append(phrase);
        }
        byte[] full = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (full.length == size) return full;
        byte[] result = new byte[size];
        System.arraycopy(full, 0, result, 0, size);
        return result;
    }

    public static byte[] randomBytes(int size) {
        var rng = new Random(42);
        byte[] data = new byte[size];
        rng.nextBytes(data);
        return data;
    }
}
