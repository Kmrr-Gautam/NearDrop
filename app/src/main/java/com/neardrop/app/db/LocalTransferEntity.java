package com.neardrop.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_transfers")
public class LocalTransferEntity {
    @PrimaryKey
    @NonNull
    public String transferId;

    public String sessionCode;
    public String peerDeviceName;
    public String peerIp;
    public String direction; // "SEND" or "RECEIVE"
    public String status;    // "COMPLETED", "FAILED", "CANCELLED"
    public int fileCount;
    public long totalBytes;
    public long transferredBytes;
    public long timestamp;
    public String idempotencyKey;

    public LocalTransferEntity(@NonNull String transferId, String sessionCode, String peerDeviceName, String peerIp, String direction, String status, int fileCount, long totalBytes, long transferredBytes, long timestamp, String idempotencyKey) {
        this.transferId = transferId;
        this.sessionCode = sessionCode;
        this.peerDeviceName = peerDeviceName;
        this.peerIp = peerIp;
        this.direction = direction;
        this.status = status;
        this.fileCount = fileCount;
        this.totalBytes = totalBytes;
        this.transferredBytes = transferredBytes;
        this.timestamp = timestamp;
        this.idempotencyKey = idempotencyKey;
    }
}
