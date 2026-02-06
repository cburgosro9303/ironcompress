package io.ironcompress.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeErrorTest {

    @Test
    void fromCodeRoundtrip() {
        for (NativeError err : NativeError.values()) {
            assertEquals(err, NativeError.fromCode(err.code()));
        }
    }

    @Test
    void unknownCodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> NativeError.fromCode(42));
        assertThrows(IllegalArgumentException.class, () -> NativeError.fromCode(-100));
    }

    @Test
    void isSuccess() {
        assertTrue(NativeError.SUCCESS.isSuccess());
        assertFalse(NativeError.BUFFER_TOO_SMALL.isSuccess());
        assertFalse(NativeError.INTERNAL_ERROR.isSuccess());
    }

    @Test
    void codesMatchRust() {
        assertEquals(0, NativeError.SUCCESS.code());
        assertEquals(-1, NativeError.BUFFER_TOO_SMALL.code());
        assertEquals(-2, NativeError.ALGO_NOT_FOUND.code());
        assertEquals(-3, NativeError.INVALID_ARGUMENT.code());
        assertEquals(-50, NativeError.INTERNAL_ERROR.code());
        assertEquals(-99, NativeError.PANIC_CAUGHT.code());
    }
}
