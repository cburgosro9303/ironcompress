use std::io::{Cursor, Read, Write};

use log::trace;

use crate::error::CompressError;

/// Algorithm IDs — stable, never change.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum CompressionAlgo {
    Lz4 = 1,
    Snappy = 2,
    Zstd = 3,
    Gzip = 4,
    Brotli = 5,
    Lzma2 = 6,
    Bzip2 = 7,
    Lzf = 8,
    Deflate = 9,
}

impl TryFrom<u8> for CompressionAlgo {
    type Error = CompressError;

    fn try_from(value: u8) -> Result<Self, Self::Error> {
        match value {
            1 => Ok(CompressionAlgo::Lz4),
            2 => Ok(CompressionAlgo::Snappy),
            3 => Ok(CompressionAlgo::Zstd),
            4 => Ok(CompressionAlgo::Gzip),
            5 => Ok(CompressionAlgo::Brotli),
            6 => Ok(CompressionAlgo::Lzma2),
            7 => Ok(CompressionAlgo::Bzip2),
            8 => Ok(CompressionAlgo::Lzf),
            9 => Ok(CompressionAlgo::Deflate),
            _ => Err(CompressError::AlgoNotFound(value)),
        }
    }
}

/// Returns a clamped level for the algorithm. Negative levels map to the default.
fn clamp_level(algo: CompressionAlgo, level: i32) -> i32 {
    match algo {
        // lz4_flex has no level control — always fast mode.
        CompressionAlgo::Lz4 => 0,
        // Snappy has no level control.
        CompressionAlgo::Snappy => 0,
        // Zstd: 1-22, default 3
        CompressionAlgo::Zstd => {
            if level <= 0 {
                3
            } else {
                level.clamp(1, 22)
            }
        }
        // Gzip: 1-9, default 6 (flate2 level 0 = no compression)
        CompressionAlgo::Gzip => {
            if level <= 0 {
                6
            } else {
                level.clamp(1, 9)
            }
        }
        // Brotli: 1-11, default 6 (level 0 = nearly no compression)
        CompressionAlgo::Brotli => {
            if level <= 0 {
                6
            } else {
                level.clamp(1, 11)
            }
        }
        // LZMA2: 1-9, default 6
        CompressionAlgo::Lzma2 => {
            if level <= 0 {
                6
            } else {
                level.clamp(1, 9)
            }
        }
        // Bzip2: 1-9, default 6 (level = block size in 100KB units)
        CompressionAlgo::Bzip2 => {
            if level <= 0 {
                6
            } else {
                level.clamp(1, 9)
            }
        }
        // LZF has no level control.
        CompressionAlgo::Lzf => 0,
        // Deflate: 1-9, default 6 (flate2 level 0 = no compression)
        CompressionAlgo::Deflate => {
            if level <= 0 {
                6
            } else {
                level.clamp(1, 9)
            }
        }
    }
}

/// Compress `input` into `output` using the given algorithm and level.
/// Returns the number of bytes written to `output`.
pub fn compress(
    algo: CompressionAlgo,
    level: i32,
    input: &[u8],
    output: &mut [u8],
) -> Result<usize, CompressError> {
    let level = clamp_level(algo, level);
    trace!(
        "compress: dispatching algo={algo:?}, clamped_level={level}, input_len={}",
        input.len()
    );

    match algo {
        CompressionAlgo::Lz4 => compress_lz4(input, output),
        CompressionAlgo::Snappy => compress_snappy(input, output),
        CompressionAlgo::Gzip => compress_gzip(input, output, level),
        CompressionAlgo::Deflate => compress_deflate(input, output, level),
        CompressionAlgo::Zstd => compress_zstd(input, output, level),
        CompressionAlgo::Brotli => compress_brotli(input, output, level),
        CompressionAlgo::Lzma2 => compress_lzma2(input, output, level),
        CompressionAlgo::Bzip2 => compress_bzip2(input, output, level),
        CompressionAlgo::Lzf => compress_lzf(input, output),
    }
}

fn compress_lz4(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    lz4_flex::block::compress_into(input, output).map_err(|_| CompressError::BufferTooSmall {
        needed: lz4_flex::block::get_maximum_output_size(input.len()),
    })
}

fn compress_snappy(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let max = snap::raw::max_compress_len(input.len());
    if output.len() < max {
        return Err(CompressError::BufferTooSmall { needed: max });
    }
    let mut encoder = snap::raw::Encoder::new();
    let n = encoder.compress(input, output)?;
    Ok(n)
}

fn compress_gzip(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let cursor = Cursor::new(output);
    let mut encoder = flate2::write::GzEncoder::new(cursor, flate2::Compression::new(level as u32));
    encoder.write_all(input)?;
    let cursor = encoder.finish()?;
    Ok(cursor.position() as usize)
}

fn compress_deflate(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let cursor = Cursor::new(output);
    let mut encoder =
        flate2::write::DeflateEncoder::new(cursor, flate2::Compression::new(level as u32));
    encoder.write_all(input)?;
    let cursor = encoder.finish()?;
    Ok(cursor.position() as usize)
}

fn compress_lzf(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    // LZF returns NoCompressionPossible for very small/incompressible data.
    // We prefix with a 1-byte flag: 0x01 = compressed, 0x00 = stored raw.
    match lzf::compress(input) {
        Ok(compressed) => {
            let total = 1 + compressed.len();
            if total > output.len() {
                return Err(CompressError::BufferTooSmall { needed: total });
            }
            output[0] = 0x01; // compressed
            output[1..total].copy_from_slice(&compressed);
            Ok(total)
        }
        Err(lzf::LzfError::NoCompressionPossible) => {
            let total = 1 + input.len();
            if total > output.len() {
                return Err(CompressError::BufferTooSmall { needed: total });
            }
            output[0] = 0x00; // raw
            output[1..total].copy_from_slice(input);
            Ok(total)
        }
        Err(e) => Err(CompressError::Internal(format!("LZF compress: {e:?}"))),
    }
}

fn compress_lzma2(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let cursor = Cursor::new(output);
    let mut encoder = xz2::write::XzEncoder::new(cursor, level as u32);
    encoder.write_all(input)?;
    let cursor = encoder.finish()?;
    Ok(cursor.position() as usize)
}

fn compress_bzip2(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let cursor = Cursor::new(output);
    let mut encoder = bzip2::write::BzEncoder::new(cursor, bzip2::Compression::new(level as u32));
    encoder.write_all(input)?;
    let cursor = encoder.finish()?;
    Ok(cursor.position() as usize)
}

fn compress_zstd(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let mut compressor = zstd::bulk::Compressor::new(level)?;
    let n = compressor.compress_to_buffer(input, output)?;
    Ok(n)
}

fn compress_brotli(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let mut cursor = Cursor::new(output);
    let params = brotli::enc::BrotliEncoderParams {
        quality: level,
        ..Default::default()
    };
    brotli::BrotliCompress(&mut &input[..], &mut cursor, &params)?;
    Ok(cursor.position() as usize)
}

/// Read from a decoder directly into the output buffer, avoiding intermediate Vec allocation.
fn read_to_buffer(reader: &mut impl Read, output: &mut [u8]) -> Result<usize, CompressError> {
    let mut total = 0;
    loop {
        if total >= output.len() {
            // Buffer full — check if decoder has more data
            let mut probe = [0u8; 1];
            return match reader.read(&mut probe) {
                Ok(0) => Ok(total),
                Ok(_) => Err(CompressError::BufferTooSmall { needed: total + 1 }),
                Err(e) => Err(e.into()),
            };
        }
        match reader.read(&mut output[total..]) {
            Ok(0) => return Ok(total),
            Ok(n) => total += n,
            Err(e) => return Err(e.into()),
        }
    }
}

/// Decompress `input` into `output` using the given algorithm.
/// Returns the number of bytes written to `output`.
pub fn decompress(
    algo: CompressionAlgo,
    input: &[u8],
    output: &mut [u8],
) -> Result<usize, CompressError> {
    trace!(
        "decompress: dispatching algo={algo:?}, input_len={}",
        input.len()
    );

    match algo {
        CompressionAlgo::Lz4 => decompress_lz4(input, output),
        CompressionAlgo::Snappy => decompress_snappy(input, output),
        CompressionAlgo::Gzip => decompress_gzip(input, output),
        CompressionAlgo::Deflate => decompress_deflate(input, output),
        CompressionAlgo::Zstd => decompress_zstd(input, output),
        CompressionAlgo::Brotli => decompress_brotli(input, output),
        CompressionAlgo::Lzma2 => decompress_lzma2(input, output),
        CompressionAlgo::Bzip2 => decompress_bzip2(input, output),
        CompressionAlgo::Lzf => decompress_lzf(input, output),
    }
}

fn decompress_lz4(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    lz4_flex::block::decompress_into(input, output)
        .map_err(|e| CompressError::Internal(e.to_string()))
}

fn decompress_snappy(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let n = snap::raw::Decoder::new().decompress(input, output)?;
    Ok(n)
}

fn decompress_gzip(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let mut decoder = flate2::read::GzDecoder::new(input);
    read_to_buffer(&mut decoder, output)
}

fn decompress_deflate(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let mut decoder = flate2::read::DeflateDecoder::new(input);
    read_to_buffer(&mut decoder, output)
}

fn decompress_zstd(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let mut decompressor = zstd::bulk::Decompressor::new()?;
    let n = decompressor.decompress_to_buffer(input, output)?;
    Ok(n)
}

fn decompress_brotli(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let mut cursor = Cursor::new(output);
    brotli::BrotliDecompress(&mut &input[..], &mut cursor)?;
    Ok(cursor.position() as usize)
}

fn decompress_lzma2(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let mut decoder = xz2::read::XzDecoder::new(input);
    read_to_buffer(&mut decoder, output)
}

fn decompress_bzip2(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    let mut decoder = bzip2::read::BzDecoder::new(input);
    read_to_buffer(&mut decoder, output)
}

fn decompress_lzf(input: &[u8], output: &mut [u8]) -> Result<usize, CompressError> {
    if input.is_empty() {
        return Err(CompressError::Internal(
            "LZF decompress: empty input".into(),
        ));
    }
    let flag = input[0];
    let payload = &input[1..];
    match flag {
        0x00 => {
            // Raw stored data
            if payload.len() > output.len() {
                return Err(CompressError::BufferTooSmall {
                    needed: payload.len(),
                });
            }
            output[..payload.len()].copy_from_slice(payload);
            Ok(payload.len())
        }
        0x01 => {
            // LZF compressed data
            let decompressed = lzf::decompress(payload, output.len())
                .map_err(|e| CompressError::Internal(format!("LZF decompress: {e:?}")))?;
            if decompressed.len() > output.len() {
                return Err(CompressError::BufferTooSmall {
                    needed: decompressed.len(),
                });
            }
            output[..decompressed.len()].copy_from_slice(&decompressed);
            Ok(decompressed.len())
        }
        _ => Err(CompressError::Internal(format!(
            "LZF decompress: unknown flag {flag}"
        ))),
    }
}

/// Conservative estimate of maximum output size for a given algorithm and input length.
pub fn estimate_max_output_size(algo: CompressionAlgo, _level: i32, input_len: usize) -> usize {
    match algo {
        CompressionAlgo::Lz4 => lz4_flex::block::get_maximum_output_size(input_len),
        CompressionAlgo::Snappy => snap::raw::max_compress_len(input_len),
        CompressionAlgo::Gzip | CompressionAlgo::Deflate => input_len + input_len / 8 + 32,
        _ => input_len * 2 + 64,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Read;

    fn make_test_data() -> Vec<u8> {
        "Hello world! ".repeat(100).into_bytes()
    }

    #[test]
    fn lz4_roundtrip() {
        let input = make_test_data();
        let max = lz4_flex::block::get_maximum_output_size(input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lz4, -1, &input, &mut compressed).unwrap();
        let decompressed = lz4_flex::block::decompress(&compressed[..n], input.len()).unwrap();
        assert_eq!(input, decompressed);
    }

    #[test]
    fn snappy_roundtrip() {
        let input = make_test_data();
        let max = snap::raw::max_compress_len(input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Snappy, -1, &input, &mut compressed).unwrap();
        let decompressed = snap::raw::Decoder::new()
            .decompress_vec(&compressed[..n])
            .unwrap();
        assert_eq!(input, decompressed);
    }

    #[test]
    fn gzip_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Gzip, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Gzip, 6, &input, &mut compressed).unwrap();
        let mut decoder = flate2::read::GzDecoder::new(&compressed[..n]);
        let mut decompressed = Vec::new();
        decoder.read_to_end(&mut decompressed).unwrap();
        assert_eq!(input, decompressed);
    }

    #[test]
    fn deflate_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Deflate, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Deflate, 6, &input, &mut compressed).unwrap();
        let mut decoder = flate2::read::DeflateDecoder::new(&compressed[..n]);
        let mut decompressed = Vec::new();
        decoder.read_to_end(&mut decompressed).unwrap();
        assert_eq!(input, decompressed);
    }

    #[test]
    fn buffer_too_small_returns_hint() {
        let input = make_test_data();
        let mut tiny = [0u8; 4];
        let err = compress(CompressionAlgo::Lz4, -1, &input, &mut tiny).unwrap_err();
        match err {
            CompressError::BufferTooSmall { needed } => assert!(needed > 4),
            other => panic!("expected BufferTooSmall, got: {other}"),
        }
    }

    #[test]
    fn algo_not_found() {
        assert!(CompressionAlgo::try_from(255u8).is_err());
    }

    #[test]
    fn lzf_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Lzf, 0, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lzf, 0, &input, &mut compressed).unwrap();
        assert!(n > 0);
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Lzf, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn lzf_single_byte_roundtrip() {
        let input = vec![42u8];
        let max = estimate_max_output_size(CompressionAlgo::Lzf, 0, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lzf, 0, &input, &mut compressed).unwrap();
        assert!(n > 0);
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Lzf, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn lzf_random_roundtrip() {
        let input: Vec<u8> = (0..1024).map(|i| ((i * 37 + 13) % 256) as u8).collect();
        let max = estimate_max_output_size(CompressionAlgo::Lzf, 0, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lzf, 0, &input, &mut compressed).unwrap();
        assert!(n > 0);
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Lzf, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn lz4_decompress_roundtrip() {
        let input = make_test_data();
        let max = lz4_flex::block::get_maximum_output_size(input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lz4, -1, &input, &mut compressed).unwrap();
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Lz4, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn snappy_decompress_roundtrip() {
        let input = make_test_data();
        let max = snap::raw::max_compress_len(input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Snappy, -1, &input, &mut compressed).unwrap();
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Snappy, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn gzip_decompress_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Gzip, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Gzip, 6, &input, &mut compressed).unwrap();
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Gzip, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn deflate_decompress_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Deflate, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Deflate, 6, &input, &mut compressed).unwrap();
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(
            CompressionAlgo::Deflate,
            &compressed[..n],
            &mut decompressed,
        )
        .unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn lzma2_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Lzma2, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lzma2, 6, &input, &mut compressed).unwrap();
        assert!(n > 0);
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Lzma2, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn bzip2_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Bzip2, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Bzip2, 6, &input, &mut compressed).unwrap();
        assert!(n < input.len());
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Bzip2, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn brotli_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Brotli, 6, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Brotli, 6, &input, &mut compressed).unwrap();
        assert!(n < input.len());
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Brotli, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn zstd_roundtrip() {
        let input = make_test_data();
        let max = estimate_max_output_size(CompressionAlgo::Zstd, 3, input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Zstd, 3, &input, &mut compressed).unwrap();
        assert!(n < input.len());
        let mut decompressed = vec![0u8; input.len()];
        let m = decompress(CompressionAlgo::Zstd, &compressed[..n], &mut decompressed).unwrap();
        assert_eq!(m, input.len());
        assert_eq!(&decompressed[..m], &input[..]);
    }

    #[test]
    fn decompress_buffer_too_small() {
        let input = make_test_data();
        let max = lz4_flex::block::get_maximum_output_size(input.len());
        let mut compressed = vec![0u8; max];
        let n = compress(CompressionAlgo::Lz4, -1, &input, &mut compressed).unwrap();
        let mut tiny = vec![0u8; 4];
        let err = decompress(CompressionAlgo::Lz4, &compressed[..n], &mut tiny).unwrap_err();
        // LZ4 decompress with too-small output should fail
        assert!(
            err.to_code() == crate::error::INTERNAL_ERROR
                || err.to_code() == crate::error::BUFFER_TOO_SMALL
        );
    }
}
