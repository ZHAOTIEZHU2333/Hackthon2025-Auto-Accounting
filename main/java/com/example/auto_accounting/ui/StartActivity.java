package com.example.auto_accounting.ui;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.NotificationCompat;

import com.example.auto_accounting.R;
import com.example.auto_accounting.core.TrackingManager;
import com.example.auto_accounting.data.repo.TableWriter;
import com.example.auto_accounting.notify.GPayListenerService;

/**
 * 启动页：
 * 1) 点击 Start：设置全局“开始采集”开关 → 尝试重绑监听服务 → 跳转图表页。
 * 2) 长按 Start：弹出调试菜单（发一条模拟支付通知 / 直接写一条测试数据到 DB）。
 */
public class StartActivity extends AppCompatActivity {

    private Button btnStart;

    // Android 13+ 发送通知需要动态权限
    private ActivityResultLauncher<String> postNotifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        btnStart = findViewById(R.id.button_start);

        // 点击：开启收集并进入图表页
        btnStart.setOnClickListener(v -> {
            // 1) 开启全局采集开关
            TrackingManager.setEnabled(getApplicationContext(), true);

            // 2) 触发重绑，让 NotificationListener 立即生效（API 24+）
            try {
                if (Build.VERSION.SDK_INT >= 24) {
                    NotificationListenerService.requestRebind(
                            new ComponentName(this, GPayListenerService.class));
                }
            } catch (Throwable ignored) { /* 某些机型可能无权限，忽略即可 */ }

            // 3) 跳转图表页
            startActivity(new Intent(this, ChartActivity.class));
            finish();
        });

        // 长按：弹出调试菜单
        btnStart.setOnLongClickListener(v -> {
            showDebugMenu(v);
            return true;
        });

        // Android 13+ 请求 POST_NOTIFICATIONS 权限（仅用于“发送测试通知”）
        postNotifPermLauncher = registerForActivityResult(
                new RequestPermission(),
                granted -> {
                    if (granted) {
                        sendTestPaymentNotification();
                    } else {
                        Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // -------------------------------- 调试功能 --------------------------------

    /** 显示调试菜单：发送测试通知 / 直接写入一条测试数据 */
    private void showDebugMenu(@NonNull android.view.View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Send test payment notification");
        menu.getMenu().add(0, 2, 1, "Insert one test row to DB");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                maybeRequestPostNotificationAndSend();
                return true;
            } else if (item.getItemId() == 2) {
                insertOneTestRow();
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** Android 13+ 先请求通知权限，再发送测试通知 */
    private void maybeRequestPostNotificationAndSend() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                postNotifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        sendTestPaymentNotification();
    }

    /**
     * 发送一条“像 GPay 的支付成功”测试通知。
     * 监听服务在 debuggable 构建下会处理自家包名的通知，便于本地调试。
     */
    private void sendTestPaymentNotification() {
        final String channelId = "test_pay";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    channelId, "Test Pay", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        // 这段文案能被解析器识别到金额与商家
        String title = "Payment successful";
        String text  = "You paid $12.34 at Starbucks";

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true);

        nm.notify(10086, nb.build());
        Toast.makeText(this, "Sent test payment notification", Toast.LENGTH_SHORT).show();
    }

    /** 直接向数据库插入一条当月的测试数据（跳过通知链路） */
    private void insertOneTestRow() {
        long now = System.currentTimeMillis();
        TableWriter.save(getApplicationContext(), now, "Starbucks", Math.round(12.34 * 100));
        Toast.makeText(this, "Inserted one test row to DB", Toast.LENGTH_SHORT).show();
    }
}
