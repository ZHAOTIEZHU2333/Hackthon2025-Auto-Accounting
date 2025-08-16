package com.example.auto_accounting.data.repo;

import android.content.Context;
import com.example.auto_accounting.data.db.DbProvider;
import com.example.auto_accounting.data.db.Table;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Writes parsed rows to the database on a single background thread.
 *  *解析结果 → TableWriter.save(ctx, time, desc, amountMinor)
 *  *         → DbProvider.get(ctx)        // 取到 AppDb 单例
 *  *         → AppDb.tableDao().insert()  // DAO 执行插入
 *  *         → SQLite 文件 simple_table.db
 */
public final class TableWriter {

    /** Single-threaded executor for DB IO. */
    private static final ExecutorService EXECUTOR_IO =
            Executors.newSingleThreadExecutor();

    private TableWriter() {
        // No instances.
    }

    /**
     * Saves one parsed row into the DB asynchronously.
     *
     * @param context     any context
     * @param timeMillis  event time in epoch milliseconds
     * @param description description text
     * @param amountMinor amount in cents
     */
    public static void save(
            Context context,
            long timeMillis,
            String description,
            long amountMinor
    ) {
        EXECUTOR_IO.execute(() -> {
            Table row = new Table();
            row.timeMillis = timeMillis;
            row.description = description;
            row.amountMinor = amountMinor;
            DbProvider.get(context).tableDao().insert(row);
        });
    }
}

