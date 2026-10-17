package com.neardrop.app.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public interface TransferDao {

    // Devices
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDevice(LocalDeviceEntity device);

    @Query("SELECT * FROM local_devices ORDER BY lastSeenTimestamp DESC")
    List<LocalDeviceEntity> getDevices();

    // Transfers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransfer(LocalTransferEntity transfer);

    @Query("SELECT * FROM local_transfers ORDER BY timestamp DESC")
    List<LocalTransferEntity> getAllTransfers();

    @Query("SELECT * FROM local_transfers WHERE transferId = :id LIMIT 1")
    LocalTransferEntity getTransferById(String id);

    @Query("UPDATE local_transfers SET status = :status, transferredBytes = :transferredBytes WHERE transferId = :id")
    void updateTransferStatus(String id, String status, long transferredBytes);

    // Files
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFile(LocalTransferFileEntity file);

    @Query("SELECT * FROM local_transfer_files WHERE transferId = :transferId")
    List<LocalTransferFileEntity> getFilesForTransfer(String transferId);

    // Events
    @Insert
    void insertEvent(LocalTransferEventEntity event);

    @Query("SELECT * FROM local_transfer_events WHERE transferId = :transferId ORDER BY timestamp ASC")
    List<LocalTransferEventEntity> getEventsForTransfer(String transferId);

    // Pending Sync
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void queueForSync(PendingSyncEntity pending);

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    List<PendingSyncEntity> getPendingSyncItems();

    @Query("DELETE FROM pending_sync WHERE idempotencyKey = :key")
    void deletePendingSync(String key);
}
