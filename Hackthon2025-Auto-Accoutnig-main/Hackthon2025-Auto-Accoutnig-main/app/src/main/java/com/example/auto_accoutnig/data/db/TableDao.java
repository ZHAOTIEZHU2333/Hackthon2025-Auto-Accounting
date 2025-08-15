package com.example.auto_accoutnig.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;


/**
 * 数据访问接口
 * 写入工具 TableWriter.java
 * 接口 save
 */
@Dao
public interface TableDao {
    @Insert
    long insert(Table t);  // 后台调用

    @Query("SELECT * FROM table_entries WHERE timeMillis >= :startMillis AND timeMillis < :endMillis ORDER BY timeMillis ASC")
    List<Table> listInRange(long startMillis, long endMillis);
}

}