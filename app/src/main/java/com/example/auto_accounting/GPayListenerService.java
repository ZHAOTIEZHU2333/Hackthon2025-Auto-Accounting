package com.example.auto_accounting.notify;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import com.example.auto_accounting.storage.CsvHelper;
import com.example.auto_accounting.storage.ReportExporter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监听 Google Pay / Google Wallet 通知，解析金额与商家，写入 CSV，并导出汇总与图表。
 * 注意：用户仍需在系统“设置 → 通知 → 特殊访问权限 → 通知访问”中手动开启本应用。
 */
public class GPayListenerService extends NotificationListenerService {

    private static final String TAG = "GPayListener";

    /**
     * 常见 Google 支付相关包名：
     * - com.google.android.apps.walletnfcrel   (Google Wallet/Pay)
     * - com.google.android.apps.nbu.paisa.user (Google Pay India)
     * 可按你的设备实测补充/调整
     */
    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user"
    ));

    // 金额匹配：支持 $、AUD$、千分位逗号、小数两位
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:AUD\\s*)?\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+(?:\\.[0-9]{1,2})?)"
    );

    // 尝试从英文/中文关键字后提取商家名
    private static final Pattern[] MERCHANT_HINTS = new Pattern[] {
            Pattern.compile("\\bat\\s+([A-Za-z0-9&\\-\\*\\.#\\s]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bto\\s+([A-Za-z0-9&\\-\\*\\.#\\s]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwith\\s+([A-Za-z0-9&\\-\\*\\.#\\s]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("向([\\p{L}A-Za-z0-9&\\-\\*\\.#\\s]{2,60})"),
            Pattern.compile("给([\\p{L}A-Za-z0-9&\\-\\*\\.#\\s]{2,60})")
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Notification listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        final String pkg = sbn.getPackageName();
        if (TextUtils.isEmpty(pkg)) return;

        // 只处理 Google Pay / Wallet
        if (!TARGET_PACKAGES.contains(pkg)) {
            return;
        }

        try {
            Bundle extras = sbn.getNotification() != null ? sbn.getNotification().extras : null;
            if (extras == null) return;

            String title = safeString(extras.getString(Notification.EXTRA_TITLE));
            String text = safeCharSeq(extras.getCharSequence(Notification.EXTRA_TEXT));
            String big = safeCharSeq(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));

            // 拼接内容以便统一解析
            String content = joinNonBlank(" | ", title, text, big);

            // 粗过滤：只处理包含“支付/付款/paid/purchase/payment”等关键词的通知
            if (!containsPaymentKeyword(content)) {
                Log.d(TAG, "No payment keyword found, skip. content=" + content);
                return;
            }

            Double amount = parseAmount(content);
            String merchant = parseMerchant(title, text, big);

            if (amount != null && !TextUtils.isEmpty(merchant)) {
                Log.i(TAG, "Parsed OK: amount=" + amount + ", merchant=" + merchant);

                // 写入 CSV 并导出报表/图表
                // 说明：这两个类稍后我会给你 Java 版本文件（storage 包）
                CsvHelper.appendRow(amount, merchant);
                ReportExporter.generateAll();
            } else {
                Log.w(TAG, "Parse failed. content=" + content + ", title=" + title + ", text=" + text + ", big=" + big);
            }

        } catch (Throwable t) {
            Log.e(TAG, "onNotificationPosted error", t);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (sbn != null) {
            Log.d(TAG, "Notification removed from: " + sbn.getPackageName());
        }
    }

    // ======== 解析工具 ========

    private static boolean containsPaymentKeyword(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.contains("paid")
                || lower.contains("payment")
                || lower.contains("purchase")
                || lower.contains("purchased")
                || s.contains("支付")
                || s.contains("付款")
                || s.contains("已支付")
                || s.contains("已付款");
    }

    /** 从完整内容里解析金额，兼容 $、AUD$、千分位逗号、小数 */
    private static Double parseAmount(String content) {
        if (TextUtils.isEmpty(content)) return null;
        Matcher m = AMOUNT_PATTERN.matcher(content);
        if (m.find()) {
            String raw = m.group(1).replace(",", "");
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    /**
     * 尝试解析商家名：
     * 1) 先根据 at/with/to/向/给 等关键词抓取
     * 2) 再从 title 去掉金额后兜底
     */
    private static String parseMerchant(String title, String text, String big) {
        String joined = joinNonBlank(" | ", title, text, big);
        // 去掉金额，避免把金额当成商家
        String cleaned = AMOUNT_PATTERN.matcher(joined).replaceAll("").trim();

        for (Pattern p : MERCHANT_HINTS) {
            Matcher m = p.matcher(cleaned);
            if (m.find()) {
                String cand = trimMerchant(m.group(1));
                if (!TextUtils.isEmpty(cand)) return cand;
            }
        }

        // 兜底：用标题去掉金额后当商家
        String fallback = AMOUNT_PATTERN.matcher(safeString(title)).replaceAll("").trim();
        if (!TextUtils.isEmpty(fallback)) {
            return trimMerchant(fallback);
        }
        return "UnknownMerchant";
    }

    private static String trimMerchant(String s) {
        if (s == null) return null;
        // 去掉分隔符、竖线等噪音，并限制长度
        String t = s.replace("|", " ")
                .replace("•", " ")
                .replace("…", " ")
                .trim();
        // 常见尾部噪声
        t = t.replaceAll("\\s+\\-*\\s*$", "");
        if (t.length() > 60) t = t.substring(0, 60);
        return t;
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private static String safeCharSeq(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private static String joinNonBlank(String sep, String... arr) {
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (!TextUtils.isEmpty(s)) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(s);
            }
        }
        return sb.toString();
    }
}