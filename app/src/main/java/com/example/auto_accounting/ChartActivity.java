package com.example.auto_accounting;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import java.io.OutputStream;
import java.util.concurrent.FutureTask;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.auto_accounting.R;
import com.example.auto_accounting.data.export.MonthlyBarChartExporter;

import java.io.InputStream;
import java.time.YearMonth;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;

import com.example.auto_accounting.data.db.DbProvider;
import com.example.auto_accounting.data.db.Table;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 图表页：预览“本月按日合计柱状图”，并导出为 PNG。
 * - 预览/导出都放到后台线程（单线程池）；
 * - 导出使用 SAF（CreateDocument），无需外部存储权限；
 * - 预览图保存在内存里，Activity 销毁时回收。
 */
public class ChartActivity extends AppCompatActivity {

    // --- UI ---
    private TextView textStatus;
    private Button btnPreview;
    private Button btnExport;
    private ImageView imageChart;

    // --- 异步与缓存 ---
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private @Nullable Bitmap currentPreview;
    private TableLayout tableTransactions;

    // SAF：创建 PNG 文档
    private ActivityResultLauncher<String> createPngLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        textStatus = findViewById(R.id.text_status);
        btnPreview  = findViewById(R.id.button_preview);
        btnExport   = findViewById(R.id.button_export_png);
        imageChart  = findViewById(R.id.image_chart);
        tableTransactions = findViewById(R.id.table_transactions);

        // 预览
        btnPreview.setOnClickListener(v -> {
            // 点击“MONTHLY SPEND”按钮：先显示数据库表格，再在下方生成当月图表
            loadAndShowTransactions();
            previewCurrentMonth();
        });

        // 导出：弹出“保存为”对话框，用户选择文件名/位置
        createPngLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/png"),
                uri -> { if (uri != null) exportCurrentMonthTo(uri); }
        );
        btnExport.setOnClickListener(v -> createPngLauncher.launch(defaultFileName()));

        textStatus.setText("Ready");
    }

    // ------------------ 业务方法 ------------------

    /** 生成“本月”柱状图并在页面预览（后台线程） */
    private void previewCurrentMonth() {
        textStatus.setText("Generating preview…");
        io.execute(() -> {
            try {
                Bitmap bmp = MonthlyBarChartExporter.buildCurrentMonthBitmap(this);
                runOnUiThread(() -> {
                    setPreviewBitmap(bmp);
                    textStatus.setText("Ready");
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textStatus.setText("Failed: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** 导出“本月”柱状图到指定 Uri（后台线程），完成后预览导出的文件 */
    private void exportCurrentMonthTo(Uri uri) {
        textStatus.setText("Exporting…");
        io.execute(() -> {
            try {
                // 1) 准备柱状图位图（若当前预览为空则重新生成）
                Bitmap chart = currentPreview;
                if (chart == null || chart.isRecycled()) {
                    chart = MonthlyBarChartExporter.buildCurrentMonthBitmap(this);
                }

                // 2) 捕获表格位图（在主线程同步测量绘制），宽度对齐图表
                final int targetWidth = chart.getWidth();
                Bitmap tableBmp = captureTableBitmapSync(targetWidth);

                // 3) 组合：表格在上，图表在下；若表格为空则仅导出图表
                Bitmap combined = (tableBmp != null) ? combineVertical(tableBmp, chart) : chart;

                // 4) 写入 PNG
                ContentResolver cr = getContentResolver();
                try (OutputStream os = cr.openOutputStream(uri, "w")) {
                    if (os == null) throw new IllegalStateException("Cannot open output stream");
                    combined.compress(Bitmap.CompressFormat.PNG, 100, os);
                }

                runOnUiThread(() -> {
                    setPreviewBitmap(combined);
                    textStatus.setText("Exported successfully");
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textStatus.setText("Failed: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** 生成默认文件名：monthly_spending_YYYY_MM.png */
    private String defaultFileName() {
        YearMonth ym = YearMonth.now();
        return "monthly_spending_" + ym.getYear() + "_" +
                String.format("%02d", ym.getMonthValue()) + ".png";
    }

    /** 切换当前预览位图，负责回收旧图，避免内存泄漏 */
    private void setPreviewBitmap(@Nullable Bitmap bmp) {
        if (currentPreview != null && !currentPreview.isRecycled()) {
            currentPreview.recycle();
        }
        currentPreview = bmp;
        imageChart.setImageBitmap(bmp);
    }

    /** 加载数据库并渲染到表格（后台线程加载，主线程渲染） */
    private void loadAndShowTransactions() {
        textStatus.setText("Loading records…");
        io.execute(() -> {
            try {
                List<Table> rows = DbProvider.get(getApplicationContext())
                        .tableDao()
                        .recent(200); // 读取最近 200 条，可按需调整
                runOnUiThread(() -> {
                    renderTable(rows);
                    textStatus.setText("Ready");
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textStatus.setText("Load failed: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** 把数据行渲染为一个 TableLayout */
    private void renderTable(List<Table> rows) {
        tableTransactions.removeAllViews();

        // 表头
        TableRow header = new TableRow(this);
        header.addView(makeCell("时间", true));
        header.addView(makeCell("商家", true));
        header.addView(makeCell("金额(元)", true));
        tableTransactions.addView(header);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (Table r : rows) {
            TableRow tr = new TableRow(this);
            tr.addView(makeCell(sdf.format(new Date(r.timeMillis)), false));
            tr.addView(makeCell(r.description == null ? "" : r.description, false));
            tr.addView(makeCell(formatAmount((long) r.amountMinor), false));
            tableTransactions.addView(tr);
        }
    }

    private String formatAmount(long minor) {
        return String.format(Locale.getDefault(), "%.2f", minor / 100.0);
    }

    private TextView makeCell(String text, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v,
                getResources().getDisplayMetrics()
        );
    }

    /** 在主线程同步捕获 TableLayout 的完整位图（包含所有行），宽度按 targetWidth 渲染 */
    @Nullable
    private Bitmap captureTableBitmapSync(int targetWidth) {
        if (tableTransactions == null) return null;
        FutureTask<Bitmap> task = new FutureTask<>(() -> renderViewToBitmap(tableTransactions, targetWidth));
        runOnUiThread(task);
        try {
            return task.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 把任意 View 渲染成位图（按给定宽度，测量高度为 wrap-content 全部内容） */
    private static Bitmap renderViewToBitmap(View view, int targetWidth) {
        int wSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(wSpec, hSpec);
        int w = view.getMeasuredWidth();
        int h = view.getMeasuredHeight();
        view.layout(0, 0, w, h);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        return bmp;
    }

    /** 纵向拼接两张位图：top 在上，bottom 在下；宽度取较大值并居左绘制 */
    private static Bitmap combineVertical(Bitmap top, Bitmap bottom) {
        int width = Math.max(top.getWidth(), bottom.getWidth());
        int height = top.getHeight() + bottom.getHeight();
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(top, 0, 0, null);
        c.drawBitmap(bottom, 0, top.getHeight(), null);
        return out;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
        if (currentPreview != null && !currentPreview.isRecycled()) {
            currentPreview.recycle();
        }
    }
}
