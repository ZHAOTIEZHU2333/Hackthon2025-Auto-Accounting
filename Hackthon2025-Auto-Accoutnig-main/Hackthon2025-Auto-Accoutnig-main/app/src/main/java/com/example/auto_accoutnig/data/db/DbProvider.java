package com.example.auto_accoutnig.data.db;

import android.content.Context;
import androidx.room.Room;

public final class DbProvider {
    private static volatile AppDb INSTANCE;
    private DbProvider() {}

    public static AppDb get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (DbProvider.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            ctx.getApplicationContext(),
                            AppDb.class,
                            "simple_table.db"   // 数据库文件名
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
