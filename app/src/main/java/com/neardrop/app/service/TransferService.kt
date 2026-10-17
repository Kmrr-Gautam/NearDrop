package com.neardrop.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.neardrop.app.MainActivity
import com.neardrop.app.NearDropApplication
import com.neardrop.app.R
import com.neardrop.app.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

import android.net.Uri
import com.neardrop.app.update.AppUpdateInfo

object TransferNotifications {
    const val CH_ONGOING = "ongoing"
    const val CH_RESULT = "results"
    const val CH_UPDATES = "updates"
    const val ID_FOREGROUND = 1001
    const val ID_UPDATE = 9999

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Transfers in progress", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Transfer results and requests", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CH_UPDATES, "App Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Notifications when NearDrop updates are ready to install"
                }
            )
        }
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun notifyUpdate(ctx: Context, info: AppUpdateInfo) {
        if (!canNotify(ctx)) return
        ensureChannels(ctx)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notif = NotificationCompat.Builder(ctx, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("NearDrop Update Available: v${info.versionName}")
            .setContentText("Tap to download & install the latest updates.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "NearDrop v${info.versionName} is ready to install!\n" + info.whatsNew.joinToString("\n") { "• $it" }
                )
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Download Update", pi)
            .build()

        ctx.getSystemService(NotificationManager::class.java).notify(ID_UPDATE, notif)
    }

    fun ongoing(ctx: Context, active: List<TransferProgress>, receiving: Boolean): Notification {
        val b = NotificationCompat.Builder(ctx, CH_ONGOING).setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open(ctx))
        if (active.isEmpty()) {
            b.setContentTitle("NearDrop is ready to receive").setContentText(if (receiving) "Visible to nearby devices" else "Idle")
        } else {
            val t = active.first()
            val verb = if (t.direction == Direction.SEND) "Sending to" else "Receiving from"
            b.setContentTitle("$verb ${t.peerName}")
                .setContentText("${Fmt.bytes(t.bytesDone)} / ${Fmt.bytes(t.totalBytes)} · ${Fmt.speed(t.speedBps)}")
                .setProgress(1000, (t.fraction * 1000).toInt(), t.status != TransferStatus.TRANSFERRING)
                .addAction(0, "Cancel", PendingIntent.getService(ctx, 1, Intent(ctx, TransferService::class.java).setAction(TransferService.ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE))
        }
        return b.build()
    }

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun incoming(ctx: Context, sender: String, count: Int) {
        if (!canNotify(ctx)) return
        ctx.getSystemService(NotificationManager::class.java).notify(1002,
            NotificationCompat.Builder(ctx, CH_RESULT).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$sender wants to send $count file(s)").setContentText("Tap to review")
                .setContentIntent(open(ctx)).setAutoCancel(true).build())
    }

    fun finished(ctx: Context, peer: String, dir: Direction, s: TransferStatus, msg: String?) {
        if (!canNotify(ctx)) return
        val title = when (s) {
            TransferStatus.COMPLETED -> if (dir == Direction.SEND) "Sent to $peer" else "Received from $peer"
            TransferStatus.CANCELLED -> "Transfer cancelled"
            TransferStatus.REJECTED -> "Transfer declined"
            TransferStatus.INTERRUPTED -> "Transfer interrupted"
            else -> "Transfer failed"
        }
        val notifBuilder = NotificationCompat.Builder(ctx, CH_RESULT).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(msg ?: "").setContentIntent(open(ctx)).setAutoCancel(true)
        if (s == TransferStatus.COMPLETED && dir == Direction.RECEIVE) {
            val folderIntent = NearDropFileManager.getFolderIntent(ctx)
            if (folderIntent != null) {
                val pi = PendingIntent.getActivity(
                    ctx,
                    (System.nanoTime() % 100000).toInt(),
                    folderIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                notifBuilder.addAction(0, "Open folder", pi)
            }
        }
        ctx.getSystemService(NotificationManager::class.java).notify((System.nanoTime() % 100000).toInt() + 2000, notifBuilder.build())
    }
}

/** Foreground service (type dataSync) that keeps the process alive while receiving or transferring. */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as NearDropApplication).engine
        if (intent?.action == ACTION_CANCEL) engine.cancelAll()
        TransferNotifications.ensureChannels(this)
        val first = TransferNotifications.ongoing(this, emptyList(), engine.receiving.value)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, TransferNotifications.ID_FOREGROUND, first, type)
        job?.cancel()
        job = scope.launch {
            combine(engine.progress, engine.receiving) { p, r -> p.values.filter { !it.status.terminal } to r }.collect { (active, rcv) ->
                if (active.isEmpty() && !rcv) { stopSelf(); return@collect }
                getSystemService(NotificationManager::class.java).notify(TransferNotifications.ID_FOREGROUND, TransferNotifications.ongoing(this@TransferService, active, rcv))
            }
        }
        return START_NOT_STICKY // process death ends transfers; partial data stays on disk and the transfer can be resumed
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_CANCEL = "com.neardrop.app.CANCEL"
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, TransferService::class.java))
    }
}
