package com.example.auto_accounting;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Start 页面：
 * 1) 点击“开始”后，开启全局采集（TrackingManager），并尝试重绑监听服务 GPayListenerService（让监听立刻生效）。
 * 2) 立刻写入一条测试数据到本地表（用于验证链路 & 展示），真正的通知数据由 GPayListenerService 在收到通知时直接写入表。
 * 3) 跳转到数据库/图表页面 ChartActivity。
 */
public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        Button btnStart = findViewById(R.id.btnStart);

        btnStart.setOnClickListener(v -> {
            // 1) 开启全局采集开关（若你有 TrackingManager）
            try {
                TrackingManager.setEnabled(getApplicationContext(), true);
            } catch (Throwable ignored) { /* 如果没有该类，忽略即可 */ }

            // 2) 触发重绑，让 NotificationListener 立即生效（API 24+）
            try {
                NotificationListenerService.requestRebind(
                        new ComponentName(this, GPayListenerService.class));
            } catch (Throwable ignored) { /* 某些机型可能无权限，忽略即可 */ }

            // 3) 不再由 StartActivity 主动写库；GPayListenerService 在收到通知后会自动入库
            Toast.makeText(this, "已开始监听：解析到的通知将自动保存", Toast.LENGTH_SHORT).show();

            // 4) 跳转到数据库/图表页面
            startActivity(new Intent(this, ChartActivity.class));
            finish();
        });
    }
}
