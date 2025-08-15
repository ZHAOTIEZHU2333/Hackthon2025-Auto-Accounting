package com.example.auto_accoutnig.data.db;

import android.content.Context;
import androidx.room.Room;

/**
 * 数据库单例
 * 提供全局复用AppDb 实例 防止多处各建一份DB 造成资源浪费 */

public final class DbProvider {
    private static volatile AppDb INSTANCE;
    private DbProvider() {}

    public static AppDb get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (DbProvider.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            ctx.getApplicationContext(),//避免内存泄露
                            AppDb.class,
                            "simple_table.db"   // 数据库文件名
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
