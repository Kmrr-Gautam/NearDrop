package com.neardrop.app.api;

import android.content.Context;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

public class TransferRepository {
    private final ApiService apiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface TransferCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public TransferRepository(Context context) {
        this.apiService = RetrofitClient.getService(context);
    }

    public void createSession(String senderDeviceId, int fileCount, long totalBytes, String idempotencyKey, List<ApiModels.FileMetadataItem> files, TransferCallback<ApiModels.SessionData> callback) {
        executor.execute(() -> {
            try {
                ApiModels.SessionCreateRequest req = new ApiModels.SessionCreateRequest(senderDeviceId, fileCount, totalBytes, idempotencyKey, files);
                Response<ApiModels.ApiResponse<ApiModels.SessionData>> resp = apiService.createSession(req).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                    callback.onSuccess(resp.body().data);
                } else {
                    callback.onError("Create session failed: " + resp.code());
                }
            } catch (Exception e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    public void joinSession(String transferId, String sessionCode, String receiverDeviceId, TransferCallback<ApiModels.SessionData> callback) {
        executor.execute(() -> {
            try {
                ApiModels.SessionJoinRequest req = new ApiModels.SessionJoinRequest(sessionCode, receiverDeviceId);
                Response<ApiModels.ApiResponse<ApiModels.SessionData>> resp = apiService.joinSession(transferId, req).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                    callback.onSuccess(resp.body().data);
                } else {
                    callback.onError("Join session failed: " + resp.code());
                }
            } catch (Exception e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    public void completeSession(String transferId, TransferCallback<ApiModels.SessionData> callback) {
        executor.execute(() -> {
            try {
                Response<ApiModels.ApiResponse<ApiModels.SessionData>> resp = apiService.completeTransfer(transferId).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                    callback.onSuccess(resp.body().data);
                } else {
                    callback.onError("Complete session failed: " + resp.code());
                }
            } catch (Exception e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }
}
