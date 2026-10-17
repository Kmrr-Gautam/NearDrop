package com.neardrop.app.api;

import android.content.Context;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // 10.0.2.2 is Android emulator's alias to host machine localhost
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8000/";

    private static volatile ApiService apiService;
    private static volatile TokenManager tokenManager;

    public static TokenManager getTokenManager(Context context) {
        if (tokenManager == null) {
            synchronized (RetrofitClient.class) {
                if (tokenManager == null) {
                    tokenManager = new TokenManager(context.getApplicationContext());
                }
            }
        }
        return tokenManager;
    }

    public static ApiService getService(Context context) {
        if (apiService == null) {
            synchronized (RetrofitClient.class) {
                if (apiService == null) {
                    apiService = createService(context.getApplicationContext(), DEFAULT_BASE_URL);
                }
            }
        }
        return apiService;
    }

    public static ApiService createService(Context context, String baseUrl) {
        TokenManager tm = getTokenManager(context);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(new AuthInterceptor(tm))
            .addInterceptor(logging)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        return retrofit.create(ApiService.class);
    }
}
