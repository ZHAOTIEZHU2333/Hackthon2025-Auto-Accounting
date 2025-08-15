package com.example.auto_accouting.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Room database holding the {@link Table} entity.
 */
@Database(entities = {Table.class}, version = 1, exportSchema = false)
public abstract class AppDb extends RoomDatabase {

    /**
     * @return the DAO for {@link Table}.
     */
    public abstract TableDao tableDao();
}
