//! NearDrop Secure Session - Nonce management, key derivation, and SAS generation.

use hmac::{Hmac, Mac};
use sha2::{Digest, Sha256};
use thiserror::Error;

type HmacSha256 = Hmac<Sha256>;

#[derive(Error, Debug)]
pub enum SecurityError {
    #[error("Key derivation error: {0}")]
    KeyDerivation(String),
    #[error("Nonce overflow")]
    NonceOverflow,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u32)]
pub enum Direction {
    InitiatorToResponder = 0,
    ResponderToInitiator = 1,
}

/// Computes the 12-byte AES-256-GCM nonce:
/// 4-byte big-endian direction + 8-byte big-endian frame counter.
pub fn format_nonce(direction: Direction, counter: u64) -> [u8; 12] {
    let mut nonce = [0u8; 12];
    nonce[0..4].copy_from_slice(&(direction as u32).to_be_bytes());
    nonce[4..12].copy_from_slice(&counter.to_be_bytes());
    nonce
}

/// Computes the 6-digit Short Authentication String (SAS) from the handshake transcript hash:
/// SAS = (first 4 bytes of SHA-256("sas" || th), top bit cleared, as uint32) mod 1,000,000, formatted as %06d.
pub fn compute_sas(transcript_hash: &[u8; 32]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(b"sas");
    hasher.update(transcript_hash);
    let digest = hasher.finalize();

    let num = u32::from_be_bytes([
        digest[0] & 0x7F,
        digest[1],
        digest[2],
        digest[3],
    ]);
    format!("{:06}", num % 1_000_000)
}

/// Derives the unidirectional session traffic keys from the PRK and context label:
/// `c2s = HMAC(prk, "neardrop-v1 c2s" || 0x01)`
/// `s2c = HMAC(prk, "neardrop-v1 s2c" || 0x01)`
pub fn derive_traffic_keys(prk: &[u8; 32]) -> Result<([u8; 32], [u8; 32]), SecurityError> {
    let derive = |label: &[u8]| -> Result<[u8; 32], SecurityError> {
        let mut mac = HmacSha256::new_from_slice(prk)
            .map_err(|e| SecurityError::KeyDerivation(e.to_string()))?;
        mac.update(label);
        mac.update(&[0x01]);
        let result = mac.finalize().into_bytes();
        let mut key = [0u8; 32];
        key.copy_from_slice(&result);
        Ok(key)
    };

    let c2s = derive(b"neardrop-v1 c2s")?;
    let s2c = derive(b"neardrop-v1 s2c")?;
    Ok((c2s, s2c))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_nonce() {
        let nonce = format_nonce(Direction::InitiatorToResponder, 42);
        assert_eq!(&nonce[0..4], &0u32.to_be_bytes());
        assert_eq!(&nonce[4..12], &42u64.to_be_bytes());
    }

    #[test]
    fn test_sas_format() {
        let th = [0x5Au8; 32];
        let sas = compute_sas(&th);
        assert_eq!(sas.len(), 6);
        assert!(sas.chars().all(|c| c.is_ascii_digit()));
    }
}
