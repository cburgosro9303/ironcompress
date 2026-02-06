package io.ironcompress.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IronCompressExceptionTest {

    @Test
    void fieldsPopulated() {
        var ex = new IronCompressException(
                NativeError.BUFFER_TOO_SMALL, Algorithm.LZ4, 4096);
        assertEquals(NativeError.BUFFER_TOO_SMALL, ex.error());
        assertEquals(Algorithm.LZ4, ex.algorithm());
        assertEquals(4096, ex.sizeHint());
        assertTrue(ex.getMessage().contains("BUFFER_TOO_SMALL"));
        assertTrue(ex.getMessage().contains("LZ4"));
        assertTrue(ex.getMessage().contains("4096"));
    }

    @Test
    void nullAlgorithm() {
        var ex = new IronCompressException(NativeError.PANIC_CAUGHT);
        assertEquals(NativeError.PANIC_CAUGHT, ex.error());
        assertNull(ex.algorithm());
        assertEquals(-1, ex.sizeHint());
    }

    @Test
    void defaultSizeHint() {
        var ex = new IronCompressException(NativeError.INTERNAL_ERROR, Algorithm.GZIP);
        assertEquals(-1, ex.sizeHint());
    }

    @Test
    void isCheckedException() {
        assertTrue(Exception.class.isAssignableFrom(IronCompressException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(IronCompressException.class));
    }
}
