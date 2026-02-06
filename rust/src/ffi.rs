use std::panic::{catch_unwind, AssertUnwindSafe};

use crate::compress::{self, CompressionAlgo};
use crate::error;

#[unsafe(no_mangle)]
pub extern "C" fn native_ping() -> i32 {
    match catch_unwind(AssertUnwindSafe(|| 1i32)) {
        Ok(v) => v,
        Err(_) => error::PANIC_CAUGHT,
    }
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
    match catch_unwind(AssertUnwindSafe(|| {
        compress_native_inner(algo, level, in_ptr, in_len, out_ptr, out_cap, out_len)
    })) {
        Ok(code) => code,
        Err(_) => error::PANIC_CAUGHT,
    }
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
        return error::INVALID_ARGUMENT;
    }

    let compression_algo = match CompressionAlgo::try_from(algo) {
        Ok(a) => a,
        Err(e) => return e.to_code(),
    };

    let input = unsafe { std::slice::from_raw_parts(in_ptr, in_len) };
    let output = unsafe { std::slice::from_raw_parts_mut(out_ptr, out_cap) };

    match compress::compress(compression_algo, level, input, output) {
        Ok(n) => {
            unsafe { *out_len = n };
            error::SUCCESS
        }
        Err(e) => {
            if let Some(needed) = e.needed_size() {
                unsafe { *out_len = needed };
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
        Some(compress::estimate_max_output_size(compression_algo, level, in_len))
    })) {
        Ok(Some(size)) => size,
        _ => 0,
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
            1, -1,
            std::ptr::null(), 10,
            out.as_mut_ptr(), out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::INVALID_ARGUMENT);

        // null output pointer
        let input = b"hello";
        let result = compress_native(
            1, -1,
            input.as_ptr(), input.len(),
            std::ptr::null_mut(), 64,
            &mut out_len,
        );
        assert_eq!(result, error::INVALID_ARGUMENT);

        // null out_len pointer
        let result = compress_native(
            1, -1,
            input.as_ptr(), input.len(),
            out.as_mut_ptr(), out.len(),
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
            255, -1,
            input.as_ptr(), input.len(),
            out.as_mut_ptr(), out.len(),
            &mut out_len,
        );
        assert_eq!(result, error::ALGO_NOT_FOUND);
    }

    #[test]
    fn estimate_unknown_algo_returns_zero() {
        assert_eq!(estimate_max_output_size_native(255, -1, 1000), 0);
    }
}
