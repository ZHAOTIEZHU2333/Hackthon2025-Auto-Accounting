package com.example.auto_accoutnig.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * 数据库容器
 * 如更改表结构 version+1 并migration
 */
@Database(entities = { Table.class }, version = 1, exportSchema = false)
public abstract class AppDb extends RoomDatabase {
    public abstract TableDao tableDao();
}