package com.example.auto_accounting;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监听所有 App 通知，解析 金额/商家/时间，并通过广播抛出事件。
 * 解析成功后通过应用内广播通知主界面显示（仅限本应用接收）。
 */
public class GPayListenerService extends NotificationListenerService {

    private static final String TAG = "GPayListener";

    /** 事件常量：Action 与 Extra Key（给同事用） */
    public static final String ACTION_GPAY_PAYMENT_DETECTED =
            "com.example.auto_accounting.ACTION_GPAY_PAYMENT_DETECTED";
    public static final String EXTRA_AMOUNT    = "amount";      // double
    public static final String EXTRA_MERCHANT  = "merchant";    // String
    public static final String EXTRA_TIMESTAMP = "timestamp";   // long (System.currentTimeMillis)
    public static final String EXTRA_RAW_TEXT  = "raw_text";    // String（原始拼接文本, 便于二次解析）



    /** 关键词，用于粗过滤“像支付通知” */
    private static final String[] PAYMENT_KWS = new String[]{
            "paid", "payment", "purchase", "purchased", "spent", "charged",
            "已支付", "已付款", "支付", "付款", "扣款", "消费", "交易成功",
            "微信支付", "收款", "收款通知", "已收款", "转账", "收到转账", "已到账"
    };

    /** 金额匹配：支持 $ / A$ / AUD、千分位逗号、小数 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:(?:A\\$|AUD\\s*|HK\\$|¥|￥|RMB\\s*|CNY\\s*)?\\s*\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+(?:\\.[0-9]{1,2})?))(?:\\s*元)?"
    );

    /** 商家线索：英文/中文介词后接商家名 */
    private static final Pattern[] MERCHANT_HINTS = new Pattern[]{
            Pattern.compile("\\bat\\s+([A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bto\\s+([A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwith\\s+([A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("向([\\p{L}A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})"),
            Pattern.compile("给([\\p{L}A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})"),
            Pattern.compile("来自([\\p{L}A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})"),
            Pattern.compile("商户[:：]\\s*([\\p{L}A-Za-z0-9&\\-\\*\\.#'\\s]{2,80})")
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
        // 不再按包名过滤；仅跳过本应用自身的通知，避免自触发
        if (TextUtils.isEmpty(pkg) || pkg.equals(getPackageName())) return;

        try {
            Bundle extras = sbn.getNotification() != null ? sbn.getNotification().extras : null;
            if (extras == null) return;

            String title = safeString(extras.getString(Notification.EXTRA_TITLE));
            String text  = safeCharSeq(extras.getCharSequence(Notification.EXTRA_TEXT));
            String big   = safeCharSeq(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));

            String raw = normalize(joinNonBlank(" | ", title, text, big));
            Log.d(TAG, "Raw notify: pkg=" + pkg + " title=" + title + " text=" + text + " big=" + big + " | merged=" + raw);

            if (!looksLikePayment(raw)) {
                Log.d(TAG, "Skip non-payment notify. pkg=" + pkg + " raw=" + raw);
                return;
            }

            Double amount = parseAmount(raw);
            String merchant = parseMerchant(title, text, big);

            if (amount == null || TextUtils.isEmpty(merchant)) {
                Log.w(TAG, "Parse failed. raw=" + raw);
                return;
            }

            long ts = System.currentTimeMillis();
            Log.i(TAG, "Parsed -> amount=" + amount + ", merchant=" + merchant + ", ts=" + ts);

            // === 核心：发一条应用内广播给同事 ===
            Intent event = new Intent(ACTION_GPAY_PAYMENT_DETECTED);
            event.putExtra(EXTRA_AMOUNT, amount);
            event.putExtra(EXTRA_MERCHANT, merchant);
            event.putExtra(EXTRA_TIMESTAMP, ts);
            event.putExtra(EXTRA_RAW_TEXT, raw);
            // 仅限本应用接收，主界面动态接收并显示到 activity_main.xml 的日志区域
            event.setPackage(getPackageName());
            Log.d(TAG, "UI event prepared -> amount=" + amount + " merchant=" + merchant + " ts=" + ts);
            sendBroadcast(event);

        } catch (Throwable t) {
            Log.e(TAG, "onNotificationPosted error", t);
        }
    }

    // ================== 解析工具 ==================

    private static boolean looksLikePayment(String s) {
        if (TextUtils.isEmpty(s)) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        for (String kw : PAYMENT_KWS) {
            if (lower.contains(kw) || s.contains(kw)) return true;
        }
        return false;
    }

    private static Double parseAmount(String content) {
        if (TextUtils.isEmpty(content)) return null;
        Matcher m = AMOUNT_PATTERN.matcher(content);
        if (m.find()) {
            String raw = m.group(1).replace(",", "");
            try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String parseMerchant(String title, String text, String big) {
        String joined = normalize(joinNonBlank(" | ", title, text, big));
        String cleaned = AMOUNT_PATTERN.matcher(joined).replaceAll("").trim();

        for (Pattern p : MERCHANT_HINTS) {
            Matcher m = p.matcher(cleaned);
            if (m.find()) {
                String cand = tidyMerchant(m.group(1));
                if (!TextUtils.isEmpty(cand)) return cand;
            }
        }
        String fallback = AMOUNT_PATTERN.matcher(safeString(title)).replaceAll("").trim();
        return !TextUtils.isEmpty(fallback) ? tidyMerchant(fallback) : "UnknownMerchant";
    }

    private static String tidyMerchant(String s) {
        if (s == null) return null;
        String t = s.replace("|", " ")
                .replace("•", " ")
                .replace("…", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        t = t.replaceAll("[\\s\\-·]+$", "");
        if (t.length() > 80) t = t.substring(0, 80);
        return t;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String safeString(String s) { return s == null ? "" : s; }
    private static String safeCharSeq(CharSequence cs) { return cs == null ? "" : cs.toString(); }
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