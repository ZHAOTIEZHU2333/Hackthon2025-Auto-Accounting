package com.example.auto_accounting.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.auto_accounting.R;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        Button btnNotifAccess = findViewById(R.id.btnNotifAccess);
        Button btnContinue = findViewById(R.id.btnContinue);

        btnNotifAccess.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        btnContinue.setOnClickListener(v ->
                startActivity(new Intent(this, StartActivity.class)));

        updateStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        String ok = isNotificationListenerEnabled(this) ? "granted" : "not granted";
        tvStatus.setText("Notification access: " + ok);
    }

    public static boolean isNotificationListenerEnabled(Context context) {
        String flat = Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (flat == null || flat.isEmpty()) return false;
        String pkg = context.getPackageName();
        for (String s : flat.split(":")) if (s != null && s.contains(pkg)) return true;
        return false;
    }
}

