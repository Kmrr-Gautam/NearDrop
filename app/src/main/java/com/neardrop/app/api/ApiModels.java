package com.neardrop.app.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class ApiModels {

    public static class ApiResponse<T> {
        @SerializedName("success")
        public boolean success;

        @SerializedName("message")
        public String message;

        @SerializedName("data")
        public T data;
    }

    public static class RegisterRequest {
        public String email;
        public String username;
        public String password;
        @SerializedName("full_name")
        public String fullName;

        public RegisterRequest(String email, String username, String password, String fullName) {
            this.email = email;
            this.username = username;
            this.password = password;
            this.fullName = fullName;
        }
    }

    public static class LoginRequest {
        @SerializedName("username_or_email")
        public String usernameOrEmail;
        public String password;

        public LoginRequest(String usernameOrEmail, String password) {
            this.usernameOrEmail = usernameOrEmail;
            this.password = password;
        }
    }

    public static class TokenData {
        @SerializedName("access_token")
        public String accessToken;

        @SerializedName("refresh_token")
        public String refreshToken;

        @SerializedName("token_type")
        public String tokenType;

        @SerializedName("expires_in")
        public int expiresIn;
    }

    public static class RefreshRequest {
        @SerializedName("refresh_token")
        public String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class UserData {
        public String id;
        public String email;
        public String username;
        @SerializedName("full_name")
        public String fullName;
        @SerializedName("is_active")
        public boolean isActive;
    }

    public static class DeviceRegisterRequest {
        @SerializedName("device_name")
        public String deviceName;
        @SerializedName("device_model")
        public String deviceModel;
        @SerializedName("android_version")
        public String androidVersion;
        @SerializedName("app_version")
        public String appVersion;
        @SerializedName("device_token")
        public String deviceToken;

        public DeviceRegisterRequest(String deviceName, String deviceModel, String androidVersion, String appVersion, String deviceToken) {
            this.deviceName = deviceName;
            this.deviceModel = deviceModel;
            this.androidVersion = androidVersion;
            this.appVersion = appVersion;
            this.deviceToken = deviceToken;
        }
    }

    public static class DeviceData {
        public String id;
        @SerializedName("device_name")
        public String deviceName;
        @SerializedName("device_model")
        public String deviceModel;
        @SerializedName("android_version")
        public String androidVersion;
        @SerializedName("app_version")
        public String appVersion;
        @SerializedName("device_token")
        public String deviceToken;
    }

    public static class SessionCreateRequest {
        @SerializedName("sender_device_id")
        public String senderDeviceId;
        @SerializedName("file_count")
        public int fileCount;
        @SerializedName("total_size_bytes")
        public long totalSizeBytes;
        @SerializedName("idempotency_key")
        public String idempotencyKey;
        public List<FileMetadataItem> files;

        public SessionCreateRequest(String senderDeviceId, int fileCount, long totalSizeBytes, String idempotencyKey, List<FileMetadataItem> files) {
            this.senderDeviceId = senderDeviceId;
            this.fileCount = fileCount;
            this.totalSizeBytes = totalSizeBytes;
            this.idempotencyKey = idempotencyKey;
            this.files = files;
        }
    }

    public static class FileMetadataItem {
        @SerializedName("file_name")
        public String fileName;
        @SerializedName("mime_type")
        public String mimeType;
        @SerializedName("size_bytes")
        public long sizeBytes;
        @SerializedName("sha256_checksum")
        public String sha256Checksum;

        public FileMetadataItem(String fileName, String mimeType, long sizeBytes, String sha256Checksum) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.sha256Checksum = sha256Checksum;
        }
    }

    public static class SessionData {
        public String id;
        @SerializedName("session_code")
        public String sessionCode;
        @SerializedName("sender_device_id")
        public String senderDeviceId;
        @SerializedName("receiver_device_id")
        public String receiverDeviceId;
        public String status;
        @SerializedName("file_count")
        public int fileCount;
        @SerializedName("total_size_bytes")
        public long totalSizeBytes;
        @SerializedName("expires_at")
        public String expiresAt;
        public List<FileData> files;
    }

    public static class FileData {
        public String id;
        @SerializedName("file_name")
        public String fileName;
        @SerializedName("mime_type")
        public String mimeType;
        @SerializedName("size_bytes")
        public long sizeBytes;
        @SerializedName("sha256_checksum")
        public String sha256Checksum;
        public String status;
    }

    public static class SessionJoinRequest {
        @SerializedName("session_code")
        public String sessionCode;
        @SerializedName("receiver_device_id")
        public String receiverDeviceId;

        public SessionJoinRequest(String sessionCode, String receiverDeviceId) {
            this.sessionCode = sessionCode;
            this.receiverDeviceId = receiverDeviceId;
        }
    }

    public static class SessionUpdateRequest {
        public String status;
        @SerializedName("file_count")
        public Integer fileCount;
        @SerializedName("total_size_bytes")
        public Long totalSizeBytes;

        public SessionUpdateRequest(String status) {
            this.status = status;
        }
    }

    public static class EventRecordRequest {
        @SerializedName("event_type")
        public String eventType;
        public String message;
        @SerializedName("event_metadata")
        public Map<String, Object> eventMetadata;

        public EventRecordRequest(String eventType, String message, Map<String, Object> eventMetadata) {
            this.eventType = eventType;
            this.message = message;
            this.eventMetadata = eventMetadata;
        }
    }

    public static class OfflineSyncRequest {
        public List<OfflineSyncSessionItem> sessions;

        public OfflineSyncRequest(List<OfflineSyncSessionItem> sessions) {
            this.sessions = sessions;
        }
    }

    public static class OfflineSyncSessionItem {
        @SerializedName("idempotency_key")
        public String idempotencyKey;
        @SerializedName("sender_device_id")
        public String senderDeviceId;
        @SerializedName("receiver_device_id")
        public String receiverDeviceId;
        public String status;
        @SerializedName("file_count")
        public int fileCount;
        @SerializedName("total_size_bytes")
        public long totalSizeBytes;
        public List<FileMetadataItem> files;
        public List<EventRecordRequest> events;
    }

    public static class OfflineSyncResult {
        @SerializedName("synced_count")
        public int syncedCount;
        @SerializedName("session_ids")
        public List<String> sessionIds;
    }

    public static class AppConfigData {
        @SerializedName("project_name")
        public String projectName;
        public String environment;
        @SerializedName("min_app_version")
        public String minAppVersion;
        @SerializedName("latest_app_version")
        public String latestAppVersion;
        @SerializedName("allowed_protocol_versions")
        public List<String> allowedProtocolVersions;
        @SerializedName("max_files_per_transfer")
        public int maxFilesPerTransfer;
        @SerializedName("max_metadata_size_bytes")
        public long maxMetadataSizeBytes;
        @SerializedName("session_expire_minutes")
        public int sessionExpireMinutes;
    }
}
