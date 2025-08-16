package com.example.auto_accounting;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

        // 预览
        btnPreview.setOnClickListener(v -> previewCurrentMonth());

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
                ContentResolver cr = getContentResolver();
                MonthlyBarChartExporter.exportCurrentMonth(this, cr, uri);

                // 重新从导出的文件读取并预览（确保与落盘一致）
                try (InputStream is = cr.openInputStream(uri)) {
                    Bitmap exported = BitmapFactory.decodeStream(is);
                    runOnUiThread(() -> {
                        setPreviewBitmap(exported);
                        textStatus.setText("Exported successfully");
                    });
                }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
        if (currentPreview != null && !currentPreview.isRecycled()) {
            currentPreview.recycle();
        }
    }
}

