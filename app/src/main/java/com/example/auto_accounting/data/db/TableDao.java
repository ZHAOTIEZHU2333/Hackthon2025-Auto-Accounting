package com.example.auto_accounting.data.db;

import androidx.room.Query;
import androidx.room.Dao;
import androidx.room.Insert;

import java.util.List;

/**
 * Data access object for {@link Table}.
 */
@Dao
public interface TableDao {

    /**
     * Inserts one record. Call from a background thread.
     *
     * @param row the row to insert
     * @return row id
     */
    @Insert
    long insert(Table row);

    /**
     * Lists rows within [startMillis, endMillis) ordered by time ascending.
     *
     * @param startMillis inclusive start in epoch milliseconds
     * @param endMillis   exclusive end in epoch milliseconds
     * @return list of rows
     */
    @Query("SELECT * FROM table_entries "
            + "WHERE timeMillis >= :startMillis AND timeMillis < :endMillis "
            + "ORDER BY timeMillis ASC")
    List<Table> listInRange(long startMillis, long endMillis);

    @Query("SELECT * FROM table_entries ORDER BY timeMillis DESC LIMIT :limit")
    List<Table> recent(int limit);
}