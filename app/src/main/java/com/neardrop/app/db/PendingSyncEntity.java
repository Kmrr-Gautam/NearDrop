package com.neardrop.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_sync")
public class PendingSyncEntity {
    @PrimaryKey
    @NonNull
    public String idempotencyKey;

    public String transferId;
    public String senderDeviceId;
    public String receiverDeviceId;
    public String status;
    public int fileCount;
    public long totalSizeBytes;
    public long createdAt;

    public PendingSyncEntity(@NonNull String idempotencyKey, String transferId, String senderDeviceId, String receiverDeviceId, String status, int fileCount, long totalSizeBytes, long createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.transferId = transferId;
        this.senderDeviceId = senderDeviceId;
        this.receiverDeviceId = receiverDeviceId;
        this.status = status;
        this.fileCount = fileCount;
        this.totalSizeBytes = totalSizeBytes;
        this.createdAt = createdAt;
    }
}
