package com.neardrop.app.api;

import android.content.Context;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

public class DeviceRepository {
    private final ApiService apiService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface DeviceCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public DeviceRepository(Context context) {
        this.apiService = RetrofitClient.getService(context);
    }

    public void registerCurrentDevice(String deviceName, String deviceToken, DeviceCallback<ApiModels.DeviceData> callback) {
        executor.execute(() -> {
            try {
                ApiModels.DeviceRegisterRequest req = new ApiModels.DeviceRegisterRequest(
                    deviceName,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    "0.2.2",
                    deviceToken
                );
                Response<ApiModels.ApiResponse<ApiModels.DeviceData>> resp = apiService.registerDevice(req).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                    callback.onSuccess(resp.body().data);
                } else {
                    callback.onError("Device registration failed: " + resp.code());
                }
            } catch (Exception e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }
}
