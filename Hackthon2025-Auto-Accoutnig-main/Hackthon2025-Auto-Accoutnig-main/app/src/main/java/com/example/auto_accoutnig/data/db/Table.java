package com.example.auto_accoutnig.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 数据库包含三列：时间、描述、金额(分)
 * 表名 table_entries
 */
@Entity(tableName = "table_entries")
public class Table {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timeMillis;      // 时间（毫秒）
    public String description;   // 描述
    public long amountMinor;     // 金额（分）
}

}