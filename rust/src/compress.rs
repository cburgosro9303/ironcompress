use std::io::Write;

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
            if level < 0 { 3 } else { level.clamp(1, 22) }
        }
        // Gzip: 0-9, default 6
        CompressionAlgo::Gzip => {
            if level < 0 { 6 } else { level.clamp(0, 9) }
        }
        // Brotli: 0-11, default 6
        CompressionAlgo::Brotli => {
            if level < 0 { 6 } else { level.clamp(0, 11) }
        }
        // LZMA2: 0-9, default 6
        CompressionAlgo::Lzma2 => {
            if level < 0 { 6 } else { level.clamp(0, 9) }
        }
        // Bzip2: 1-9, default 6
        CompressionAlgo::Bzip2 => {
            if level < 0 { 6 } else { level.clamp(1, 9) }
        }
        // LZF has no level control.
        CompressionAlgo::Lzf => 0,
        // Deflate: 0-9, default 6
        CompressionAlgo::Deflate => {
            if level < 0 { 6 } else { level.clamp(0, 9) }
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

    match algo {
        CompressionAlgo::Lz4 => compress_lz4(input, output),
        CompressionAlgo::Snappy => compress_snappy(input, output),
        CompressionAlgo::Gzip => compress_gzip(input, output, level),
        CompressionAlgo::Deflate => compress_deflate(input, output, level),
        CompressionAlgo::Zstd
        | CompressionAlgo::Brotli
        | CompressionAlgo::Lzma2
        | CompressionAlgo::Bzip2
        | CompressionAlgo::Lzf => Err(CompressError::Internal(format!(
            "{algo:?} not yet implemented"
        ))),
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
    let mut encoder =
        flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::new(level as u32));
    encoder.write_all(input)?;
    let compressed = encoder.finish()?;
    if compressed.len() > output.len() {
        return Err(CompressError::BufferTooSmall {
            needed: compressed.len(),
        });
    }
    output[..compressed.len()].copy_from_slice(&compressed);
    Ok(compressed.len())
}

fn compress_deflate(input: &[u8], output: &mut [u8], level: i32) -> Result<usize, CompressError> {
    let mut encoder =
        flate2::write::DeflateEncoder::new(Vec::new(), flate2::Compression::new(level as u32));
    encoder.write_all(input)?;
    let compressed = encoder.finish()?;
    if compressed.len() > output.len() {
        return Err(CompressError::BufferTooSmall {
            needed: compressed.len(),
        });
    }
    output[..compressed.len()].copy_from_slice(&compressed);
    Ok(compressed.len())
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
        let decompressed =
            lz4_flex::block::decompress(&compressed[..n], input.len()).unwrap();
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
    fn stub_algorithms_return_internal_error() {
        let input = b"test";
        let mut out = vec![0u8; 256];
        for id in [3u8, 5, 6, 7, 8] {
            let algo = CompressionAlgo::try_from(id).unwrap();
            let err = compress(algo, -1, input, &mut out).unwrap_err();
            assert_eq!(err.to_code(), crate::error::INTERNAL_ERROR);
        }
    }
}
