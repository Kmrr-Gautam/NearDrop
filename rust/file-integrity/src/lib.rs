//! NearDrop File Integrity - SHA-256 verification and resume offset checking.

use hex;
use sha2::{Digest, Sha256};
use thiserror::Error;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum IntegrityError {
    #[error("Checksum mismatch: expected {expected}, calculated {calculated}")]
    ChecksumMismatch {
        expected: String,
        calculated: String,
    },
    #[error("Resume offset invalid: requested {offset}, partial file size is {actual}")]
    InvalidResumeOffset { offset: u64, actual: u64 },
}

pub struct StreamHasher {
    hasher: Sha256,
    bytes_hashed: u64,
}

impl StreamHasher {
    pub fn new() -> Self {
        Self {
            hasher: Sha256::new(),
            bytes_hashed: 0,
        }
    }

    pub fn update(&mut self, chunk: &[u8]) {
        self.hasher.update(chunk);
        self.bytes_hashed += chunk.len() as u64;
    }

    pub fn bytes_hashed(&self) -> u64 {
        self.bytes_hashed
    }

    pub fn finalize_hex(self) -> String {
        hex::encode(self.hasher.finalize())
    }

    pub fn verify(self, expected_hex: &str) -> Result<(), IntegrityError> {
        let calculated = self.finalize_hex();
        if calculated.eq_ignore_ascii_case(expected_hex) {
            Ok(())
        } else {
            Err(IntegrityError::ChecksumMismatch {
                expected: expected_hex.to_string(),
                calculated,
            })
        }
    }
}

impl Default for StreamHasher {
    fn default() -> Self {
        Self::new()
    }
}

/// Verifies that a resume prefix hash matches between sender and receiver.
pub fn verify_resume_prefix(
    sender_prefix_hex: &str,
    receiver_prefix_hex: &str,
) -> bool {
    sender_prefix_hex.eq_ignore_ascii_case(receiver_prefix_hex)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_stream_hasher() {
        let mut hasher = StreamHasher::new();
        hasher.update(b"NearDrop");
        hasher.update(b" ");
        hasher.update(b"File Integrity");
        assert_eq!(hasher.bytes_hashed(), 22);

        let hash = hasher.finalize_hex();
        assert_eq!(hash.len(), 64);
    }
}
