package com.example.auto_accoutnig.data.repo;

import android.content.Context;
import com.example.auto_accoutnig.data.db.DbProvider;
import com.example.auto_accoutnig.data.db.Table;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 将解析好的三字段写入数据库；内部用单线程池在后台执行。
 * 用法：
 * TableWriter.save(context, System.currentTimeMillis(), "lunch", 3290);
 *解析结果 → TableWriter.save(ctx, time, desc, amountMinor)
 *         → DbProvider.get(ctx)        // 取到 AppDb 单例
 *         → AppDb.tableDao().insert()  // DAO 执行插入
 *         → SQLite 文件 simple_table.db */
public final class TableWriter {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private TableWriter() {}

    public static void save(Context ctx, long timeMillis, String description, long amountMinor) {
        IO.execute(() -> {
            Table t = new Table();
            t.timeMillis = timeMillis;
            t.description = description;
            t.amountMinor = amountMinor;
            DbProvider.get(ctx).tableDao().insert(t);
        });
    }
}
