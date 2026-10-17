package com.neardrop.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transfers")
data class TransferRecord(
    @PrimaryKey val id: String,
    val direction: String,
    val peerName: String,
    val peerFingerprint: String?,
    val fileCount: Int,
    val totalBytes: Long,
    val bytesDone: Long,
    val status: String,
    val message: String?,
    val startedAt: Long,
    val finishedAt: Long,
    val fileNames: String,
)

@Entity(tableName = "trusted_devices")
data class TrustedDevice(
    @PrimaryKey val fingerprint: String,
    val name: String,
    val type: String,
    val addedAt: Long,
    val lastConnectedAt: Long?,
    val autoAccept: Boolean,
)

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY finishedAt DESC LIMIT 500")
    fun observe(): Flow<List<TransferRecord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: TransferRecord)
    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM transfers")
    suspend fun clear()
}

@Dao
interface TrustedDao {
    @Query("SELECT * FROM trusted_devices ORDER BY addedAt DESC")
    fun observe(): Flow<List<TrustedDevice>>
    @Query("SELECT * FROM trusted_devices WHERE fingerprint = :fp")
    suspend fun get(fp: String): TrustedDevice?
    @Query("SELECT fingerprint FROM trusted_devices")
    suspend fun fingerprints(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(d: TrustedDevice)
    @Query("UPDATE trusted_devices SET lastConnectedAt = :t WHERE fingerprint = :fp")
    suspend fun touch(fp: String, t: Long)
    @Query("UPDATE trusted_devices SET autoAccept = :on WHERE fingerprint = :fp")
    suspend fun setAutoAccept(fp: String, on: Boolean)
    @Query("DELETE FROM trusted_devices WHERE fingerprint = :fp")
    suspend fun delete(fp: String)
    @Query("DELETE FROM trusted_devices")
    suspend fun clear()
}

/** Add one Migration per schema bump here. Version 1 is the first release, so the list is empty. */
object Migrations { val ALL: Array<Migration> = emptyArray() }

@Database(entities = [TransferRecord::class, TrustedDevice::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transfers(): TransferDao
    abstract fun trusted(): TrustedDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "neardrop.db")
                .addMigrations(*Migrations.ALL).build().also { inst = it }
        }
    }
}
