package com.neardrop.app.api;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

public class AuthRepository {
    private final ApiService apiService;
    private final TokenManager tokenManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface AuthCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public AuthRepository(Context context) {
        this.apiService = RetrofitClient.getService(context);
        this.tokenManager = RetrofitClient.getTokenManager(context);
    }

    public void login(String usernameOrEmail, String password, AuthCallback<ApiModels.TokenData> callback) {
        executor.execute(() -> {
            try {
                Response<ApiModels.ApiResponse<ApiModels.TokenData>> resp = 
                    apiService.login(new ApiModels.LoginRequest(usernameOrEmail, password)).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                    ApiModels.TokenData data = resp.body().data;
                    tokenManager.saveTokens(data.accessToken, data.refreshToken);
                    callback.onSuccess(data);
                } else {
                    callback.onError("Login failed: " + resp.code());
                }
            } catch (Exception e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    public void register(String email, String username, String password, String fullName, AuthCallback<ApiModels.UserData> callback) {
        executor.execute(() -> {
            try {
                Response<ApiModels.ApiResponse<ApiModels.UserData>> resp = 
                    apiService.register(new ApiModels.RegisterRequest(email, username, password, fullName)).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                    callback.onSuccess(resp.body().data);
                } else {
                    callback.onError("Registration failed: " + resp.code());
                }
            } catch (Exception e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }

    public boolean isLoggedIn() {
        return tokenManager.hasValidToken();
    }

    public void logout() {
        tokenManager.clear();
    }
}
