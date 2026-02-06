package io.ironcompress.model;

/**
 * Checked exception for native compression/decompression failures.
 */
public class IronCompressException extends Exception {

    private final NativeError error;
    private final Algorithm algorithm;
    private final long sizeHint;

    public IronCompressException(NativeError error, Algorithm algorithm, long sizeHint) {
        super(buildMessage(error, algorithm, sizeHint));
        this.error = error;
        this.algorithm = algorithm;
        this.sizeHint = sizeHint;
    }

    public IronCompressException(NativeError error, Algorithm algorithm) {
        this(error, algorithm, -1);
    }

    public IronCompressException(NativeError error) {
        this(error, null, -1);
    }

    public NativeError error() {
        return error;
    }

    /** May be {@code null} if the algorithm is unknown or not applicable. */
    public Algorithm algorithm() {
        return algorithm;
    }

    /** Returns the size hint (for BUFFER_TOO_SMALL), or {@code -1} if not applicable. */
    public long sizeHint() {
        return sizeHint;
    }

    private static String buildMessage(NativeError error, Algorithm algorithm, long sizeHint) {
        var sb = new StringBuilder();
        sb.append(error.name());
        if (algorithm != null) {
            sb.append(" [").append(algorithm.name()).append("]");
        }
        if (sizeHint >= 0) {
            sb.append(" (needed ").append(sizeHint).append(" bytes)");
        }
        return sb.toString();
    }
}
