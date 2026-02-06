package io.ironcompress.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmTest {

    @Test
    void fromIdRoundtrip() {
        for (Algorithm algo : Algorithm.values()) {
            assertEquals(algo, Algorithm.fromId(algo.id()));
        }
    }

    @Test
    void unknownIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> Algorithm.fromId((byte) 0));
        assertThrows(IllegalArgumentException.class, () -> Algorithm.fromId((byte) 99));
    }

    @Test
    void idsMatchRust() {
        assertEquals(1, Algorithm.LZ4.id());
        assertEquals(2, Algorithm.SNAPPY.id());
        assertEquals(3, Algorithm.ZSTD.id());
        assertEquals(4, Algorithm.GZIP.id());
        assertEquals(5, Algorithm.BROTLI.id());
        assertEquals(6, Algorithm.LZMA2.id());
        assertEquals(7, Algorithm.BZIP2.id());
        assertEquals(8, Algorithm.LZF.id());
        assertEquals(9, Algorithm.DEFLATE.id());
    }
}
