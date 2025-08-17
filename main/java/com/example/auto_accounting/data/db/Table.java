package com.example.auto_accounting.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Record with three colums: time, description and amount(in cent).
 * <p>Real name is {@code table_entries}
 */
@Entity(tableName = "table_entries")
public class Table {

    /** Auto-increment primary key. */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Event time in epoch milliseconds. */
    public long timeMillis;

    /** The description for the record. */
    public String description;

    /** Amount stored in minor units(cents) */
    public long amountMinor;
}