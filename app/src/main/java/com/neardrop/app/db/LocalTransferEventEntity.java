package com.neardrop.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_transfer_events")
public class LocalTransferEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String transferId;
    public String eventType;
    public String message;
    public long timestamp;

    public LocalTransferEventEntity(String transferId, String eventType, String message, long timestamp) {
        this.transferId = transferId;
        this.eventType = eventType;
        this.message = message;
        this.timestamp = timestamp;
    }
}
