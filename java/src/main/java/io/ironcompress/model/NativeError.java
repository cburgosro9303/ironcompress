package io.ironcompress.model;

/**
 * Error codes matching Rust {@code error.rs} constants.
 */
public enum NativeError {
    SUCCESS(0),
    BUFFER_TOO_SMALL(-1),
    ALGO_NOT_FOUND(-2),
    INVALID_ARGUMENT(-3),
    INTERNAL_ERROR(-50),
    PANIC_CAUGHT(-99);

    private final int code;

    NativeError(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * Returns the NativeError for the given integer code.
     *
     * @throws IllegalArgumentException if the code is unknown
     */
    public static NativeError fromCode(int code) {
        for (NativeError e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown error code: " + code);
    }
}
