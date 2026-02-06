use std::fmt;

// FFI error codes — stable, never change.
pub const SUCCESS: i32 = 0;
pub const BUFFER_TOO_SMALL: i32 = -1;
pub const ALGO_NOT_FOUND: i32 = -2;
pub const INVALID_ARGUMENT: i32 = -3;
pub const INTERNAL_ERROR: i32 = -50;
pub const PANIC_CAUGHT: i32 = -99;

#[derive(Debug)]
pub enum CompressError {
    BufferTooSmall { needed: usize },
    AlgoNotFound(u8),
    InvalidArgument(String),
    Internal(String),
}

impl CompressError {
    pub fn to_code(&self) -> i32 {
        match self {
            CompressError::BufferTooSmall { .. } => BUFFER_TOO_SMALL,
            CompressError::AlgoNotFound(_) => ALGO_NOT_FOUND,
            CompressError::InvalidArgument(_) => INVALID_ARGUMENT,
            CompressError::Internal(_) => INTERNAL_ERROR,
        }
    }

    pub fn needed_size(&self) -> Option<usize> {
        match self {
            CompressError::BufferTooSmall { needed } => Some(*needed),
            _ => None,
        }
    }
}

impl fmt::Display for CompressError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CompressError::BufferTooSmall { needed } => {
                write!(f, "buffer too small, need at least {needed} bytes")
            }
            CompressError::AlgoNotFound(id) => write!(f, "algorithm not found: {id}"),
            CompressError::InvalidArgument(msg) => write!(f, "invalid argument: {msg}"),
            CompressError::Internal(msg) => write!(f, "internal error: {msg}"),
        }
    }
}

impl std::error::Error for CompressError {}

impl From<snap::Error> for CompressError {
    fn from(e: snap::Error) -> Self {
        CompressError::Internal(e.to_string())
    }
}

impl From<std::io::Error> for CompressError {
    fn from(e: std::io::Error) -> Self {
        CompressError::Internal(e.to_string())
    }
}

impl From<lz4_flex::block::CompressError> for CompressError {
    fn from(_: lz4_flex::block::CompressError) -> Self {
        CompressError::BufferTooSmall {
            needed: 0, // caller should fill in the real estimate
        }
    }
}
