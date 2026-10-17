package com.neardrop.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neardrop.app.core.Perms
import com.neardrop.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }
    @After fun tearDown() = db.close()

    @Test fun transferHistoryPersists() = runBlocking {
        db.transfers().upsert(TransferRecord("t1", "SEND", "Pixel", "ab", 1, 10, 10, "COMPLETED", null, 1, 2, "a.txt"))
        assertEquals("t1", db.transfers().observe().first().single().id)
        db.transfers().upsert(TransferRecord("t1", "SEND", "Pixel", "ab", 1, 10, 4, "INTERRUPTED", "lost", 1, 3, "a.txt"))
        assertEquals("INTERRUPTED", db.transfers().observe().first().single().status)
    }

    @Test fun trustedDeviceLifecycle() = runBlocking {
        db.trusted().upsert(TrustedDevice("fp1", "Laptop", "PC", 1, null, false))
        db.trusted().setAutoAccept("fp1", true)
        assertTrue(db.trusted().get("fp1")!!.autoAccept)
        db.trusted().delete("fp1")
        assertNull(db.trusted().get("fp1"))
    }

    @Test fun permissionListIsNeverEmpty() {
        assertTrue(Perms.required().isNotEmpty())
    }
}
