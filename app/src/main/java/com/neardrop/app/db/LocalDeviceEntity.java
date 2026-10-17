package com.neardrop.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_devices")
public class LocalDeviceEntity {
    @PrimaryKey
    @NonNull
    public String deviceId;

    public String deviceName;
    public String deviceModel;
    public String lastSeenIp;
    public int lastSeenPort;
    public long lastSeenTimestamp;

    public LocalDeviceEntity(@NonNull String deviceId, String deviceName, String deviceModel, String lastSeenIp, int lastSeenPort, long lastSeenTimestamp) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;
        this.lastSeenIp = lastSeenIp;
        this.lastSeenPort = lastSeenPort;
        this.lastSeenTimestamp = lastSeenTimestamp;
    }
}
