package com.neardrop.app

import android.app.Application
import com.neardrop.app.service.TransferNotifications
import com.neardrop.app.transfer.NearDropEngine

class NearDropApplication : Application() {
    val engine: NearDropEngine by lazy { NearDropEngine(this) }
    override fun onCreate() { super.onCreate(); TransferNotifications.ensureChannels(this) }
}
