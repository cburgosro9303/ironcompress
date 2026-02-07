use std::panic::{AssertUnwindSafe, catch_unwind};

use log::{debug, error, info, trace};

use crate::compress::{self, CompressionAlgo};
use crate::error;

#[unsafe(no_mangle)]
pub extern "C" fn native_ping() -> i32 {
    trace!("native_ping called");
    catch_unwind(AssertUnwindSafe(|| 1i32)).unwrap_or(error::PANIC_CAUGHT)
}

/// Compress `in_len` bytes from `in_ptr` into the buffer at `out_ptr` (capacity `out_cap`).
/// On success, writes the number of bytes produced to `*out_len` and returns SUCCESS (0).
/// On BUFFER_TOO_SMALL, writes the needed size hint to `*out_len` and returns -1.
#[unsafe(no_mangle)]
pub extern "C" fn compress_native(
    algo: u8,
    level: i32,
    in_ptr: *const u8,
    in_len: usize,
    out_ptr: *mut u8,
    out_cap: usize,
    out_len: *mut usize,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        compress_native_inner(algo, level, in_ptr, in_len, out_ptr, out_cap, out_len)
    }))
    .unwrap_or(error::PANIC_CAUGHT)
}

fn compress_native_inner(
    algo: u8,
    level: i32,
    in_ptr: *const u8,
    in_len: usize,
    out_ptr: *mut u8,
    out_cap: usize,
    out_len: *mut usize,
) -> i32 {
    if in_ptr.is_null() || out_ptr.is_null() || out_len.is_null() {
        error!(
            "compress: null pointer argument (in={}, out={}, out_len={})",
            !in_ptr.is_null(),
            !out_ptr.is_null(),
            !out_len.is_null()
        );
        return error::INVALID_ARGUMENT;
    }

    let compression_algo = match CompressionAlgo::try_from(algo) {
        Ok(a) => a,
        Err(e) => {
            error!("compress: unknown algorithm id={algo}");
            return e.to_code();
        }
    };

    debug!(
        "compress: algo={compression_algo:?}, level={level}, in_len={in_len}, out_cap={out_cap}"
    );

    let input = unsafe { std::slice::from_raw_parts(in_ptr, in_len) };
    let output = unsafe { std::slice::from_raw_parts_mut(out_ptr, out_cap) };

    match compress::compress(compression_algo, level, input, output) {
        Ok(n) => {
            unsafe { *out_len = n };
            info!(
                "compress: algo={compression_algo:?}, {in_len} -> {n} bytes (ratio={:.2}x)",
                if n > 0 { in_len as f64 / n as f64 } else { 0.0 }
            );
            error::SUCCESS
        }
        Err(e) => {
            if let Some(needed) = e.needed_size() {
                unsafe { *out_len = needed };
                debug!("compress: buffer too small, needed={needed}");
            } else {
                error!("compress: algo={compression_algo:?}, error={e}");
            }
            e.to_code()
        }
    }
}

/// Decompress `in_len` bytes from `in_ptr` into the buffer at `out_ptr` (capacity `out_cap`).
/// On success, writes the number of bytes produced to `*out_len` and returns SUCCESS (0).
/// On BUFFER_TOO_SMALL, writes the needed size hint to `*out_len` and returns -1.
#[unsafe(no_mangle)]
pub extern "C" fn decompress_native(
    algo: u8,
    in_ptr: *const u8,
    in_len: usize,
    out_ptr: *mut u8,
    out_cap: usize,
    out_len: *mut usize,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        decompress_native_inner(algo, in_ptr, in_len, out_ptr, out_cap, out_len)
    }))
    .unwrap_or(error::PANIC_CAUGHT)
}

fn decompress_native_inner(
    algo: u8,
    in_ptr: *const u8,
    in_len: usize,
    out_ptr: *mut u8,
    out_cap: usize,
    out_len: *mut usize,
) -> i32 {
    if in_ptr.is_null() || out_ptr.is_null() || out_len.is_null() {
        error!(
            "decompress: null pointer argument (in={}, out={}, out_len={})",
            !in_ptr.is_null(),
            !out_ptr.is_null(),
            !out_len.is_null()
        );
        return error::INVALID_ARGUMENT;
    }

    let compression_algo = match CompressionAlgo::try_from(algo) {
        Ok(a) => a,
        Err(e) => {
            error!("decompress: unknown algorithm id={algo}");
            return e.to_code();
        }
    };

    debug!("decompress: algo={compression_algo:?}, in_len={in_len}, out_cap={out_cap}");

    let input = unsafe { std::slice::from_raw_parts(in_ptr, in_len) };
    let output = unsafe { std::slice::from_raw_parts_mut(out_ptr, out_cap) };

    match compress::decompress(compression_algo, input, output) {
        Ok(n) => {
            unsafe { *out_len = n };
            info!("decompress: algo={compression_algo:?}, {in_len} -> {n} bytes");
            error::SUCCESS
        }
        Err(e) => {
            if let Some(needed) = e.needed_size() {
                unsafe { *out_len = needed };
                debug!("decompress: buffer too small, needed={needed}");
            } else {
                error!("decompress: algo={compression_algo:?}, error={e}");
            }
            e.to_code()
        }
    }
}

/// Returns a conservative estimate of the maximum compressed output size.
/// Returns 0 on error (unknown algorithm or panic).
#[unsafe(no_mangle)]
pub extern "C" fn estimate_max_output_size_native(algo: u8, level: i32, in_len: usize) -> usize {
    match catch_unwind(AssertUnwindSafe(|| {
        let compression_algo = CompressionAlgo::try_from(algo).ok()?;
        let estimate = compress::estimate_max_output_size(compression_algo, level, in_len);
        trace!(
            "estimate_max_output_size: algo={compression_algo:?}, in_len={in_len}, estimate={estimate}"
        );
        Some(estimate)
    })) {
        Ok(Some(size)) => size,
        _ => {
            debug!("estimate_max_output_size: failed for algo={algo}");
            0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ping_returns_one() {
        assert_eq!(native_ping(), 1);
    }

    #[test]
    fn compress_null_ptr_returns_invalid_argument() {
        let mut out_len: usize = 0;
        let mut out = [0u8; 64];

        // null input pointer
        let result = compress_native(
            1,
            -1,
            std::ptr::null(),
            10,
            out.as_mut_ptr(),
            out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::INVALID_ARGUMENT);

        // null output pointer
        let input = b"hello";
        let result = compress_native(
            1,
            -1,
            input.as_ptr(),
            input.len(),
            std::ptr::null_mut(),
            64,
            &mut out_len,
        );
        assert_eq!(result, error::INVALID_ARGUMENT);

        // null out_len pointer
        let result = compress_native(
            1,
            -1,
            input.as_ptr(),
            input.len(),
            out.as_mut_ptr(),
            out.len(),
            std::ptr::null_mut(),
        );
        assert_eq!(result, error::INVALID_ARGUMENT);
    }

    #[test]
    fn compress_unknown_algo_returns_algo_not_found() {
        let input = b"hello";
        let mut out = [0u8; 64];
        let mut out_len: usize = 0;
        let result = compress_native(
            255,
            -1,
            input.as_ptr(),
            input.len(),
            out.as_mut_ptr(),
            out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::ALGO_NOT_FOUND);
    }

    #[test]
    fn estimate_unknown_algo_returns_zero() {
        assert_eq!(estimate_max_output_size_native(255, -1, 1000), 0);
    }

    #[test]
    fn decompress_null_ptr_returns_invalid_argument() {
        let mut out_len: usize = 0;
        let mut out = [0u8; 64];

        let result = decompress_native(
            1,
            std::ptr::null(),
            10,
            out.as_mut_ptr(),
            out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::INVALID_ARGUMENT);
    }

    #[test]
    fn decompress_unknown_algo_returns_algo_not_found() {
        let input = b"hello";
        let mut out = [0u8; 64];
        let mut out_len: usize = 0;
        let result = decompress_native(
            255,
            input.as_ptr(),
            input.len(),
            out.as_mut_ptr(),
            out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::ALGO_NOT_FOUND);
    }

    #[test]
    fn decompress_invalid_data_returns_error() {
        let input = b"hello";
        let mut out = [0u8; 64];
        let mut out_len: usize = 0;
        let result = decompress_native(
            1,
            input.as_ptr(),
            input.len(),
            out.as_mut_ptr(),
            out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::INTERNAL_ERROR);
    }
}
