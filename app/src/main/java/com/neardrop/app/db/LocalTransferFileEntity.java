package com.neardrop.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_transfer_files")
public class LocalTransferFileEntity {
    @PrimaryKey
    @NonNull
    public String fileId;

    public String transferId;
    public String fileName;
    public String mimeType;
    public long sizeBytes;
    public String sha256Checksum;
    public String localUri;
    public String status;

    public LocalTransferFileEntity(@NonNull String fileId, String transferId, String fileName, String mimeType, long sizeBytes, String sha256Checksum, String localUri, String status) {
        this.fileId = fileId;
        this.transferId = transferId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256Checksum = sha256Checksum;
        this.localUri = localUri;
        this.status = status;
    }
}
