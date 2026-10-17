package com.neardrop.app.api;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // Auth
    @POST("api/v1/auth/register")
    Call<ApiModels.ApiResponse<ApiModels.UserData>> register(@Body ApiModels.RegisterRequest body);

    @POST("api/v1/auth/login")
    Call<ApiModels.ApiResponse<ApiModels.TokenData>> login(@Body ApiModels.LoginRequest body);

    @POST("api/v1/auth/refresh")
    Call<ApiModels.ApiResponse<ApiModels.TokenData>> refresh(@Body ApiModels.RefreshRequest body);

    @GET("api/v1/auth/me")
    Call<ApiModels.ApiResponse<ApiModels.UserData>> getProfile();

    // Devices
    @POST("api/v1/devices/register")
    Call<ApiModels.ApiResponse<ApiModels.DeviceData>> registerDevice(@Body ApiModels.DeviceRegisterRequest body);

    @GET("api/v1/devices")
    Call<ApiModels.ApiResponse<List<ApiModels.DeviceData>>> listDevices();

    @GET("api/v1/devices/{device_id}")
    Call<ApiModels.ApiResponse<ApiModels.DeviceData>> getDevice(@Path("device_id") String deviceId);

    @DELETE("api/v1/devices/{device_id}")
    Call<ApiModels.ApiResponse<Object>> deleteDevice(@Path("device_id") String deviceId);

    // Transfers
    @POST("api/v1/transfers")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> createSession(@Body ApiModels.SessionCreateRequest body);

    @GET("api/v1/transfers")
    Call<ApiModels.ApiResponse<List<ApiModels.SessionData>>> listSessions();

    @GET("api/v1/transfers/{transfer_id}")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> getSession(@Path("transfer_id") String transferId);

    @PATCH("api/v1/transfers/{transfer_id}")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> updateSession(@Path("transfer_id") String transferId, @Body ApiModels.SessionUpdateRequest body);

    @POST("api/v1/transfers/{transfer_id}/join")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> joinSession(@Path("transfer_id") String transferId, @Body ApiModels.SessionJoinRequest body);

    @POST("api/v1/transfers/{transfer_id}/files")
    Call<ApiModels.ApiResponse<List<ApiModels.FileData>>> addFiles(@Path("transfer_id") String transferId, @Body List<ApiModels.FileMetadataItem> files);

    @POST("api/v1/transfers/{transfer_id}/start")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> startTransfer(@Path("transfer_id") String transferId);

    @POST("api/v1/transfers/{transfer_id}/complete")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> completeTransfer(@Path("transfer_id") String transferId);

    @POST("api/v1/transfers/{transfer_id}/cancel")
    Call<ApiModels.ApiResponse<ApiModels.SessionData>> cancelTransfer(@Path("transfer_id") String transferId);

    @POST("api/v1/transfers/{transfer_id}/events")
    Call<ApiModels.ApiResponse<Object>> recordEvent(@Path("transfer_id") String transferId, @Body ApiModels.EventRecordRequest body);

    @POST("api/v1/transfers/sync")
    Call<ApiModels.ApiResponse<ApiModels.OfflineSyncResult>> syncOfflineSessions(@Body ApiModels.OfflineSyncRequest body);

    // Config & Health
    @GET("api/v1/config")
    Call<ApiModels.ApiResponse<ApiModels.AppConfigData>> getConfig();

    @GET("health")
    Call<Object> getHealth();
}
