package io.ironcompress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeLibTest {

    @Test
    void nativePingReturnsOne() {
        assertEquals(1, NativeLib.nativePing());
    }
}
