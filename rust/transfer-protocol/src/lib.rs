//! NearDrop Wire Protocol v1 - Core message parsing, framing, and manifest validation.

use serde::{Deserialize, Serialize};
use thiserror::Error;

pub const PROTOCOL_VERSION: u32 = 1;
pub const MAGIC: u32 = 0x4E445250; // "NDRP"
pub const DEFAULT_PORT: u16 = 45454;
pub const MAX_FRAME_SIZE: usize = 8 * 1024 * 1024 + 17; // 8 MiB + AEAD overhead
pub const MAX_FILES_PER_TRANSFER: usize = 50_000;
pub const MAX_PATH_DEPTH: usize = 32;

#[derive(Error, Debug, PartialEq, Eq)]
pub enum ProtocolError {
    #[error("Invalid magic prefix: expected {MAGIC:#X}, got {0:#X}")]
    InvalidMagic(u32),
    #[error("Unsupported protocol version: {0}")]
    UnsupportedVersion(u32),
    #[error("Frame size exceeds maximum allowed: {0} > {MAX_FRAME_SIZE}")]
    FrameTooLarge(usize),
    #[error("Path traversal detected: {0}")]
    PathTraversal(String),
    #[error("Empty path segment")]
    EmptyPathSegment,
    #[error("Path exceeds maximum depth: {0} > {MAX_PATH_DEPTH}")]
    PathTooDeep(usize),
    #[error("Invalid character in path: {0}")]
    InvalidPathCharacter(char),
    #[error("Too many files: {0} > {MAX_FILES_PER_TRANSFER}")]
    TooManyFiles(usize),
    #[error("Serialization error: {0}")]
    Serialization(String),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum FrameType {
    Control = 1,
    Data = 2,
}

impl TryFrom<u8> for FrameType {
    type Error = ProtocolError;

    fn try_from(value: u8) -> Result<Self, Self::Error> {
        match value {
            1 => Ok(FrameType::Control),
            2 => Ok(FrameType::Data),
            _ => Err(ProtocolError::Serialization(format!("Unknown frame type: {value}"))),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FileMetadata {
    #[serde(rename = "i")]
    pub index: u32,
    #[serde(rename = "p")]
    pub path: String,
    #[serde(rename = "s")]
    pub size: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TransferRequest {
    #[serde(rename = "v")]
    pub version: u32,
    pub id: String,
    pub name: String,
    pub device: String,
    pub files: Vec<FileMetadata>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ResumeEntry {
    pub index: u32,
    pub offset: u64,
    pub prefix: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct TransferResponse {
    pub accept: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resume: Option<Vec<ResumeEntry>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FileStart {
    pub index: u32,
    pub offset: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FileEnd {
    pub index: u32,
    pub sha256: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FileOk {
    pub index: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct FileError {
    pub index: u32,
    pub reason: String,
}

/// Validates that a file manifest path is safe from traversal attacks, absolute paths,
/// illegal Windows/UNIX characters, and excessive directory nesting.
pub fn validate_manifest_path(path: &str) -> Result<(), ProtocolError> {
    if path.is_empty() {
        return Err(ProtocolError::EmptyPathSegment);
    }
    if path.starts_with('/') || path.starts_with('\\') {
        return Err(ProtocolError::PathTraversal(path.to_string()));
    }
    let segments: Vec<&str> = path.split('/').collect();
    if segments.len() > MAX_PATH_DEPTH {
        return Err(ProtocolError::PathTooDeep(segments.len()));
    }

    for segment in segments {
        if segment.is_empty() || segment == "." || segment == ".." {
            return Err(ProtocolError::PathTraversal(path.to_string()));
        }
        for ch in segment.chars() {
            if ch == '\\' || ch == ':' || ch == '\0' || ch.is_control() {
                return Err(ProtocolError::InvalidPathCharacter(ch));
            }
        }
    }
    Ok(())
}

/// Validates an entire transfer request manifest.
pub fn validate_request(req: &TransferRequest) -> Result<(), ProtocolError> {
    if req.version != PROTOCOL_VERSION {
        return Err(ProtocolError::UnsupportedVersion(req.version));
    }
    if req.files.len() > MAX_FILES_PER_TRANSFER {
        return Err(ProtocolError::TooManyFiles(req.files.len()));
    }
    for (expected_idx, file) in req.files.iter().enumerate() {
        if file.index as usize != expected_idx {
            return Err(ProtocolError::Serialization(format!(
                "Non-sequential file index: expected {expected_idx}, got {}",
                file.index
            )));
        }
        validate_manifest_path(&file.path)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_manifest_path() {
        assert!(validate_manifest_path("photos/vacation/img_001.jpg").is_ok());
        assert!(validate_manifest_path("document.pdf").is_ok());
    }

    #[test]
    fn test_reject_traversal() {
        assert!(validate_manifest_path("../secret.txt").is_err());
        assert!(validate_manifest_path("folder/../../evil").is_err());
        assert!(validate_manifest_path("/etc/passwd").is_err());
        assert!(validate_manifest_path("C:\\Windows\\win.ini").is_err());
    }
}
