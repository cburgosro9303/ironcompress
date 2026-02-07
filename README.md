# IronCompress

[![CI](https://github.com/cburgosro9303/ironcompress/actions/workflows/ci.yml/badge.svg)](https://github.com/cburgosro9303/ironcompress/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue.svg)](#requirements)
[![Rust 1.85+](https://img.shields.io/badge/Rust-1.85%2B-orange.svg)](#requirements)

Multi-algorithm compression for Java, powered by Rust via FFM (Foreign Function & Memory).

## Features

- **9 compression algorithms** in a single library
- **Zero-copy FFM** — off-heap `MemorySegment` buffers, no byte array copies across FFI
- **No JNI, no `--enable-preview`** — uses stable Java FFM API (Java 22+)
- **Panic firewall** — every Rust FFI function wrapped in `catch_unwind`
- **High-level and stateful APIs** — simple `byte[]` in/out or reusable buffer management

## Algorithms

| Algorithm | Rust Crate | Level Range | Default Level |
|-----------|------------|-------------|---------------|
| LZ4       | `lz4_flex` | N/A (always fast) | N/A |
| Snappy    | `snap`     | N/A         | N/A           |
| Zstd      | `zstd`     | 1-22        | 3             |
| Gzip      | `flate2`   | 1-9         | 6             |
| Brotli    | `brotli`   | 0-11        | 6             |
| LZMA2     | `xz2`      | 0-9         | 6             |
| Bzip2     | `bzip2`    | 1-9         | 6             |
| LZF       | `lzf`      | N/A         | N/A           |
| Deflate   | `flate2`   | 1-9         | 6             |

## Architecture

```
┌─────────────────────────┐       ┌──────────────────────────────┐
│  Java Application       │       │  Rust cdylib                 │
│                         │       │  (libironcompress.dylib/.so)  │
│  IronCompress API       │       │                              │
│    ├─ compress()        │──────▶│  catch_unwind(|| {           │
│    ├─ decompress()      │  FFM  │    match algorithm {         │
│    └─ estimateMaxOut()  │ down- │      LZ4 => lz4_flex,       │
│                         │ call  │      Snappy => snap,         │
│  MemorySegment (Arena)  │       │      Zstd => zstd, ...      │
│  Off-heap allocation    │       │    }                         │
└─────────────────────────┘       │  })                          │
                                  └──────────────────────────────┘
```

- **Rust cdylib**: Compression logic compiled as a C-compatible shared library.
- **Java FFM**: Calls Rust functions via `Linker.downcallHandle()` — no JNI, no `--enable-preview`.
- **Panic firewall**: Every FFI function is wrapped in `catch_unwind` — panics never escape to Java.
- **Off-heap memory**: All buffers are allocated via `Arena` as `MemorySegment` — no byte array copies across the FFI boundary.

## Quick Start

### High-Level API

```java
import io.ironcompress.IronCompress;
import io.ironcompress.model.Algorithm;

byte[] data = "Hello world! ".repeat(10_000).getBytes();

// Compress
byte[] compressed = IronCompress.compress(Algorithm.LZ4, -1, data);

// Decompress
byte[] restored = IronCompress.decompress(Algorithm.LZ4, compressed, data.length);
```

### Stateful API (Buffer Reuse)

```java
import io.ironcompress.IronCompressor;
import io.ironcompress.model.Algorithm;

try (var compressor = new IronCompressor()) {
    byte[] compressed = compressor.compress(Algorithm.ZSTD, 3, data);
    byte[] restored = compressor.decompress(Algorithm.ZSTD, compressed, data.length);
}
```

### Zero-Copy API

```java
import io.ironcompress.IronCompress;
import io.ironcompress.model.Algorithm;
import java.lang.foreign.*;

try (Arena arena = Arena.ofConfined()) {
    MemorySegment input = arena.allocate(data.length);
    input.copyFrom(MemorySegment.ofArray(data));

    long maxOut = IronCompress.estimateMaxOutputSize(Algorithm.GZIP, 6, data.length);
    MemorySegment output = arena.allocate(maxOut);

    long compressedSize = IronCompress.compress(Algorithm.GZIP, 6, input, output);
}
```

## Build

### Requirements

- Rust 1.85+ (edition 2024)
- Java 25+ (FFM stable)
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
./gradlew test -Pnative.lib.path=../rust/target/release
```

## Benchmarks

Benchmarks compare IronCompress (Rust-backed) against popular pure-Java compression libraries. Run with:

```bash
cd benchmark
./scripts/bench.sh                    # Default data sets (1KB, 100KB, 1MB text + random)
./scripts/bench.sh --size 100MB       # 100MB compressible text
./scripts/bench.sh --size 1GB         # 1GB compressible text
./scripts/bench.sh --file /path/data  # Your own file
```

### 100MB Compressible Text Results

| Algorithm | IronCompress | Java Library | Compress (ms) | Java Compress (ms) | Speedup |
|-----------|-------------|--------------|---------------|---------------------|---------|
| LZ4       | 254.96x     | lz4-java 254.97x | 5.1 | 4.0 | 0.8x |
| Snappy    | 21.02x      | snappy-java 21.02x | 7.2 | 6.1 | 0.8x |
| Zstd      | 10,859x     | zstd-jni 10,859x | 9.1 | 11.0 | 1.2x |
| Gzip      | 343.62x     | java.util.zip 343.54x | 84.9 | 282.0 | **3.3x** |
| Brotli    | 509,017x    | brotli4j 478,802x | 66.2 | 1,004.4 | **15.2x** |
| LZMA2     | 6,795x      | xz-java 6,795x | 2,135.4 | 751.2 | 0.4x |
| Bzip2     | 3,338x      | commons-compress 4,609x | 10,056.3 | 20,834.0 | **2.1x** |
| LZF       | 85.56x      | compress-lzf 81.61x | 50.5 | 7.5 | 0.1x |
| Deflate   | 343.64x     | java.util.zip 343.56x | 77.0 | 306.7 | **4.0x** |

> Ratios use zero-copy API where available. Benchmark on macOS ARM64, JDK 25, Rust 1.85.
> IronCompress excels at Gzip, Deflate, Brotli, and Bzip2 compression speed.
> LZ4/Snappy/LZF are faster in Java due to JNI-optimized native libraries with lower FFI overhead.

## Cross-Decompression Compatibility

Can data compressed by IronCompress (Rust) be decompressed by Java libraries, and vice versa?

| Algorithm | Compatible | Notes |
|-----------|------------|-------|
| Gzip      | Yes        | Standard gzip format |
| Bzip2     | Yes        | Standard bzip2 format |
| LZMA2     | Yes        | Both use XZ format |
| Zstd      | Yes        | Standard zstd frame format |
| Brotli    | Yes        | Standard brotli format |
| LZ4       | No         | lz4_flex vs lz4-java use different block formats |
| Snappy    | No         | snap vs snappy-java use different framing |
| LZF       | No         | IronCompress uses custom flag prefix (raw/compressed) |
| Deflate   | No         | flate2 uses raw deflate, Java uses zlib-wrapped |

## Error Codes

| Code | Constant           | Meaning |
|------|--------------------|---------|
| 0    | SUCCESS            | Operation completed successfully |
| -1   | BUFFER_TOO_SMALL   | Output buffer too small; `*out_len` contains the needed size hint |
| -2   | ALGO_NOT_FOUND     | Unknown algorithm ID |
| -3   | INVALID_ARGUMENT   | Null pointer or invalid parameter |
| -50  | INTERNAL_ERROR     | Internal error |
| -99  | PANIC_CAUGHT       | Rust panic caught at FFI boundary |

## Test Coverage

- **25** Rust unit tests (all algorithms, roundtrip, edge cases)
- **73** Java tests (high-level API, stateful API, zero-copy, error handling)
- **84** benchmark tests (competitor parity, cross-library comparison)

## License

[MIT](LICENSE)
