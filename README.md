# IronCompress

Multi-algorithm compression library for Java backed by Rust via FFM (Foreign Function & Memory).

## Supported Algorithms

| Algorithm | ID | Status | Level Range | Default Level |
|-----------|----|--------|-------------|---------------|
| LZ4       | 1  | Real   | N/A (always fast) | N/A |
| Snappy    | 2  | Real   | N/A         | N/A           |
| Zstd      | 3  | Stub   | 1-22        | 3             |
| Gzip      | 4  | Real   | 0-9         | 6             |
| Brotli    | 5  | Stub   | 0-11        | 6             |
| LZMA2     | 6  | Stub   | 0-9         | 6             |
| Bzip2     | 7  | Stub   | 1-9         | 6             |
| LZF       | 8  | Stub   | N/A         | N/A           |
| Deflate   | 9  | Real   | 0-9         | 6             |

**Real** = fully implemented with roundtrip-tested compression.
**Stub** = returns `INTERNAL_ERROR (-50)`; implementation planned for future fases.

## Architecture

```
Java (FFM / Panama)  ──downcall──>  Rust cdylib (libironcompress.dylib/.so/.dll)
     │                                    │
     │  MemorySegment (off-heap)          │  catch_unwind (panic firewall)
     │  Arena-scoped allocation            │  Per-algorithm dispatch
     │                                    │  Pure-Rust dependencies (no C toolchain)
```

- **Rust cdylib**: Compression logic compiled as a C-compatible shared library.
- **Java FFM**: Calls Rust functions via `Linker.downcallHandle()` — no JNI, no `--enable-preview`.
- **Panic firewall**: Every FFI function is wrapped in `catch_unwind` — panics never escape to Java.
- **Off-heap memory**: All buffers are allocated via `Arena` as `MemorySegment` — no byte array copies across the FFI boundary.

## Error Codes

| Code | Constant           | Meaning |
|------|--------------------|---------|
| 0    | SUCCESS            | Operation completed successfully |
| -1   | BUFFER_TOO_SMALL   | Output buffer too small; `*out_len` contains the needed size hint |
| -2   | ALGO_NOT_FOUND     | Unknown algorithm ID |
| -3   | INVALID_ARGUMENT   | Null pointer or invalid parameter |
| -50  | INTERNAL_ERROR     | Internal error (includes stub algorithms) |
| -99  | PANIC_CAUGHT       | Rust panic caught at FFI boundary |

## Build

### Requirements

- Rust 1.85+ (edition 2024)
- Java 22+ (FFM stable, no `--enable-preview`)
- Gradle 9.x

### Rust

```bash
cd rust
cargo build --release
cargo test
```

### Java

```bash
cd java
gradle wrapper
./gradlew test -Pnative.lib.path=../rust/target/release
```

## Usage Example

```java
import io.ironcompress.NativeLib;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

byte[] data = "Hello world! ".repeat(1000).getBytes(StandardCharsets.UTF_8);
byte algo = 1; // LZ4

try (Arena arena = Arena.ofConfined()) {
    // Allocate input
    MemorySegment input = arena.allocate(data.length);
    input.copyFrom(MemorySegment.ofArray(data));

    // Estimate output size
    long maxOut = NativeLib.estimateMaxOutputSize(algo, -1, data.length);

    // Allocate output + out_len
    MemorySegment output = arena.allocate(maxOut);
    MemorySegment outLen = arena.allocate(ValueLayout.JAVA_LONG);

    // Compress
    int result = NativeLib.compressNative(
            algo, -1,
            input, data.length,
            output, maxOut,
            outLen);

    if (result == 0) {
        long compressedSize = outLen.get(ValueLayout.JAVA_LONG, 0);
        System.out.printf("Compressed %d -> %d bytes%n", data.length, compressedSize);
    }
}
```

## License

TBD
