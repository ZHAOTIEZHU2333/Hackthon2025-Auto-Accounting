package com.example.auto_accounting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnNotifAccess;
    private Button btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnNotifAccess = findViewById(R.id.btnNotifAccess);
        btnContinue = findViewById(R.id.btnContinue);

        // 按钮1：获取“通知访问”权限（打开系统设置）
        btnNotifAccess.setOnClickListener(v -> {
            if (isNotificationListenerEnabled(MainActivity.this)) {
                Toast.makeText(MainActivity.this, "通知访问已授权", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("需要开启“通知访问”权限才能正常工作，请在设置中启用。")
                        .setPositiveButton("打开设置", (dialog, which) -> {
                            try {
                                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                        .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        });

        // 按钮2：Continue → 跳转到 StartActivity
        btnContinue.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, StartActivity.class);
            startActivity(i);
        });

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        String notifLine = "通知访问：" + (isNotificationListenerEnabled(this) ? "已启用" : "未启用");
        tvStatus.setText(notifLine);
    }

    /** 是否已授予“通知访问” */
    public static boolean isNotificationListenerEnabled(Context context) {
        String pkgName = context.getPackageName();
        String flat = Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        String[] names = flat.split(":");
        for (String name : names) {
            if (name != null && name.contains(pkgName)) {
                return true;
            }
        }
        return false;
    }
}
