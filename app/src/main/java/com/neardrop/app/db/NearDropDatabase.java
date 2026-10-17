package com.neardrop.app.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        LocalDeviceEntity.class,
        LocalTransferEntity.class,
        LocalTransferFileEntity.class,
        LocalTransferEventEntity.class,
        PendingSyncEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class NearDropDatabase extends RoomDatabase {
    private static volatile NearDropDatabase instance;

    public abstract TransferDao transferDao();

    public static NearDropDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (NearDropDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        NearDropDatabase.class,
                        "neardrop_offline.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return instance;
    }
}
