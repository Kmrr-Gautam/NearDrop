package com.neardrop.app.api;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();

        // Skip auth headers for public auth endpoints and health checks
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh") || path.contains("/health") || path.equals("/")) {
            return chain.proceed(original);
        }

        String token = tokenManager.getAccessToken();
        if (token != null && !token.isEmpty()) {
            Request authenticated = original.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();
            return chain.proceed(authenticated);
        }

        return chain.proceed(original);
    }
}
