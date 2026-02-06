package io.ironcompress.ffi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeLoaderTest {

    @Test
    void libraryLoadsAndPingWorks() {
        assertEquals(1, NativeLib.nativePing());
    }
}
