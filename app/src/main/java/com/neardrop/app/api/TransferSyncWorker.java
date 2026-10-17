package com.neardrop.app.api;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.neardrop.app.db.NearDropDatabase;
import com.neardrop.app.db.PendingSyncEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import retrofit2.Response;

public class TransferSyncWorker extends Worker {

    public TransferSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        TokenManager tm = RetrofitClient.getTokenManager(ctx);
        if (!tm.hasValidToken()) {
            return Result.success(); // Skip sync if not logged in to cloud account
        }

        NearDropDatabase db = NearDropDatabase.getInstance(ctx);
        List<PendingSyncEntity> pendingList = db.transferDao().getPendingSyncItems();
        if (pendingList == null || pendingList.isEmpty()) {
            return Result.success();
        }

        ApiService api = RetrofitClient.getService(ctx);
        List<ApiModels.OfflineSyncSessionItem> sessionItems = new ArrayList<>();

        for (PendingSyncEntity p : pendingList) {
            ApiModels.OfflineSyncSessionItem item = new ApiModels.OfflineSyncSessionItem();
            item.idempotencyKey = p.idempotencyKey;
            item.senderDeviceId = p.senderDeviceId;
            item.receiverDeviceId = p.receiverDeviceId;
            item.status = p.status;
            item.fileCount = p.fileCount;
            item.totalSizeBytes = p.totalSizeBytes;
            item.files = Collections.emptyList();
            item.events = Collections.emptyList();
            sessionItems.add(item);
        }

        try {
            Response<ApiModels.ApiResponse<ApiModels.OfflineSyncResult>> resp = 
                api.syncOfflineSessions(new ApiModels.OfflineSyncRequest(sessionItems)).execute();

            if (resp.isSuccessful() && resp.body() != null && resp.body().success) {
                for (PendingSyncEntity p : pendingList) {
                    db.transferDao().deletePendingSync(p.idempotencyKey);
                }
                return Result.success();
            } else {
                return Result.retry();
            }
        } catch (Exception e) {
            return Result.retry();
        }
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TransferSyncWorker.class)
            .setConstraints(constraints)
            .build();

        WorkManager.getInstance(context).enqueue(request);
    }
}
