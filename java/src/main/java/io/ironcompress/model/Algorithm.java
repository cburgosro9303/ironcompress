package io.ironcompress.model;

/**
 * Compression algorithms with stable byte IDs matching Rust {@code CompressionAlgo}.
 */
public enum Algorithm {
    LZ4((byte) 1),
    SNAPPY((byte) 2),
    ZSTD((byte) 3),
    GZIP((byte) 4),
    BROTLI((byte) 5),
    LZMA2((byte) 6),
    BZIP2((byte) 7),
    LZF((byte) 8),
    DEFLATE((byte) 9);

    private final byte id;

    Algorithm(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    /**
     * Returns the Algorithm for the given byte ID.
     *
     * @throws IllegalArgumentException if the ID is unknown
     */
    public static Algorithm fromId(byte id) {
        for (Algorithm a : values()) {
            if (a.id == id) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm ID: " + id);
    }
}
