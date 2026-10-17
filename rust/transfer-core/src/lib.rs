//! NearDrop Shared Transfer Core - Orchestration, state machine, and metrics.

pub use file_integrity;
pub use secure_session;
pub use transfer_protocol;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TransferStatus {
    Connecting,
    AwaitingApproval,
    Transferring,
    Verifying,
    Completed,
    Failed,
    Interrupted,
    Cancelled,
    Rejected,
}

impl TransferStatus {
    pub fn is_terminal(&self) -> bool {
        matches!(
            self,
            TransferStatus::Completed
                | TransferStatus::Failed
                | TransferStatus::Interrupted
                | TransferStatus::Cancelled
                | TransferStatus::Rejected
        )
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Direction {
    Send,
    Receive,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransferState {
    pub transfer_id: String,
    pub peer_name: String,
    pub peer_fingerprint: String,
    pub direction: Direction,
    pub status: TransferStatus,
    pub total_bytes: u64,
    pub bytes_done: u64,
    pub current_file: String,
    pub file_index: usize,
    pub file_count: usize,
    pub speed_bps: u64,
    pub avg_bps: u64,
    pub eta_seconds: u64,
    pub sas: Option<String>,
    pub message: Option<String>,
}

impl TransferState {
    pub fn new(
        transfer_id: String,
        peer_name: String,
        peer_fingerprint: String,
        direction: Direction,
        total_bytes: u64,
        file_count: usize,
    ) -> Self {
        Self {
            transfer_id,
            peer_name,
            peer_fingerprint,
            direction,
            status: TransferStatus::Connecting,
            total_bytes,
            bytes_done: 0,
            current_file: String::new(),
            file_index: 0,
            file_count,
            speed_bps: 0,
            avg_bps: 0,
            eta_seconds: 0,
            sas: None,
            message: None,
        }
    }

    pub fn fraction(&self) -> f32 {
        if self.total_bytes == 0 {
            0.0
        } else {
            (self.bytes_done as f32 / self.total_bytes as f32).clamp(0.0, 1.0)
        }
    }

    pub fn update_progress(&mut self, bytes_done: u64, elapsed_millis: u64) {
        self.bytes_done = bytes_done;
        if elapsed_millis > 0 {
            self.avg_bps = (bytes_done * 1000) / elapsed_millis;
        }
        if self.avg_bps > 0 && self.bytes_done < self.total_bytes {
            let remaining_bytes = self.total_bytes - self.bytes_done;
            self.eta_seconds = remaining_bytes / self.avg_bps;
        } else {
            self.eta_seconds = 0;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_transfer_progress_calculation() {
        let mut state = TransferState::new(
            "transfer-123".into(),
            "Pixel 8".into(),
            "abcdef0123456789".into(),
            Direction::Send,
            10_000_000,
            2,
        );
        state.update_progress(5_000_000, 1000); // 5 MB in 1s = 5 MB/s
        assert_eq!(state.fraction(), 0.5);
        assert_eq!(state.avg_bps, 5_000_000);
        assert_eq!(state.eta_seconds, 1);
    }
}
