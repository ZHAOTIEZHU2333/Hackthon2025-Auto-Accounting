package com.example.auto_accounting.core;

import android.content.Context;
import android.content.SharedPreferences;

public final class TrackingManager {
    private static final String SP = "tracking_prefs";
    private static final String KEY_ENABLED = "tracking_enabled";

    private TrackingManager() {}

    public static void setEnabled(Context ctx, boolean enabled) {
        SharedPreferences sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }
}

