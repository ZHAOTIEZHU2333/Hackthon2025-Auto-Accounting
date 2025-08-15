package com.example.auto_accouting.data.db;

import android.content.Context;
import androidx.room.Room;

/**
 * Thread-safe provider for a singleton {@link AppDb} instance.
 */
public final class DbProvider {

    /** SQLite file name. */
    private static final String DB_NAME = "simple_table.db";

    /** Singleton instance guarded by double-checked locking. */
    private static volatile AppDb instance;

    private DbProvider() {
        // No instances.
    }

    /**
     * Gets the singleton database instance using application context.
     *
     * @param context any context
     * @return app database
     */
    public static AppDb get(Context context) {
        if (instance == null) {
            synchronized (DbProvider.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDb.class,
                            DB_NAME
                    ).build();
                }
            }
        }
        return instance;
    }
}

