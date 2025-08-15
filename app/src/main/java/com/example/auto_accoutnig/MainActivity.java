package com.example.auto_accoutnig;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {
    private static final int REQ_CODE_STORAGE = 1001;

    private TextView tvStatus;
    private Button btnNotifAccess;
    private Button btnStoragePerm;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnNotifAccess = findViewById(R.id.btnNotifAccess);
        btnStoragePerm = findViewById(R.id.btnStoragePerm);

        btnNotifAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception e) {

                    e.printStackTrace();
                }
            }
        });

        btnStoragePerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStoragePermissionsIfNeeded();
            }
        });

        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        String notifLine = "Notification access is：" + (isNotificationListenerEnabled(this) ? "able" : "enable");
        String storageLine = "Memory access is：" + (hasStoragePermissions() ? "granted" : "ungranted");
        tvStatus.setText(notifLine + "\n" + storageLine);
    }


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


    private boolean hasStoragePermissions() {
        int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED;
    }


    private void requestStoragePermissionsIfNeeded() {
        if (hasStoragePermissions()) {
            updateStatus();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQ_CODE_STORAGE
        );
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_STORAGE) {
            updateStatus();
        }
    }



}
