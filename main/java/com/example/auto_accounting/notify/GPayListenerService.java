package com.example.auto_accounting.notify;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import com.example.auto_accounting.core.TrackingManager;
import com.example.auto_accounting.data.repo.TableWriter;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接收所有 App 的通知，尝试解析“支付/扣款”类消息的金额与商家，并写入本地数据库（Room）。
 * 仅当 TrackingManager.isEnabled(context) == true（用户点击 Start）时才入库。
 */
public class GPayListenerService extends NotificationListenerService {

    private static final String TAG = "GPayListener";

    // ---- 金额解析：前缀/后缀两段式，避免把“裸整数”误判为金额 ----
    private static final Pattern AMOUNT_PREFIX = Pattern.compile(
            "(?:A\\$|AU\\$|\\$|₹|€|£|¥|AUD\\s*)\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+(?:\\.[0-9]{1,2}))"
    );
    private static final Pattern AMOUNT_SUFFIX = Pattern.compile(
            "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})|[0-9]+(?:\\.[0-9]{1,2}))\\s*(?:AUD|USD|CNY|RMB|INR|EUR|GBP|JPY)\\b"
    );

    // 商家名提示关键词（英文/中文）
    private static final Pattern[] MERCHANT_HINTS = new Pattern[] {
            Pattern.compile("\\bat\\s+([A-Za-z0-9&\\-*.#\\s]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bto\\s+([A-Za-z0-9&\\-*.#\\s]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwith\\s+([A-Za-z0-9&\\-*.#\\s]{2,60})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("向([\\p{L}A-Za-z0-9&\\-*.#\\s]{2,60})"),
            Pattern.compile("给([\\p{L}A-Za-z0-9&\\-*.#\\s]{2,60})")
    };

    @Override public void onListenerConnected() { Log.i(TAG, "Notification listener connected"); }
    @Override public void onListenerDisconnected() { Log.i(TAG, "Notification listener disconnected"); }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        // 仅在“Start”之后生效
        if (!TrackingManager.isEnabled(getApplicationContext())) return;

        try {
            Notification n = sbn.getNotification();
            if (n == null) return;

            // 跳过进行中的/分组汇总的通知（一般无业务含义）
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

            Bundle extras = n.extras;
            if (extras == null) return;

            String title = safeString(extras.getString(Notification.EXTRA_TITLE));
            String text  = safeCharSeq(extras.getCharSequence(Notification.EXTRA_TEXT));
            String big   = safeCharSeq(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));

            // 多行文本也拼进去
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            StringBuilder linesJoin = new StringBuilder();
            if (lines != null) {
                for (CharSequence cs : lines) {
                    if (!TextUtils.isEmpty(cs)) {
                        if (linesJoin.length() > 0) linesJoin.append(" | ");
                        linesJoin.append(cs);
                    }
                }
            }

            String content = joinNonBlank(" | ", title, text, big, linesJoin.toString());
            if (!containsPaymentKeyword(content)) {
                // 非“支付/扣款/退款”等业务关键词，跳过
                return;
            }

            Double amount = parseAmount(content);
            String merchant = parseMerchant(title, text, big, linesJoin.toString());

            // 解析不到商家，兜底为“应用名/包名”
            if (TextUtils.isEmpty(merchant)) {
                merchant = resolveAppLabelOrPkg(sbn.getPackageName());
            }

            final long when = sbn.getPostTime();

            if (amount != null && !TextUtils.isEmpty(merchant)) {
                final long amountMinor = Math.round(amount * 100.0);
                Log.i(TAG, "Parsed OK: amount=" + amount + ", merchant=" + merchant);
                // TableWriter 内部已在单线程池异步写库
                TableWriter.save(getApplicationContext(), when, merchant, amountMinor);
            } else {
                Log.w(TAG, "Parse failed. content=" + content);
            }

        } catch (Throwable t) {
            Log.e(TAG, "onNotificationPosted error", t);
        }
    }

    // ===================== 解析与工具 =====================

    /** 粗过滤：含支付/扣款/退款等关键词才继续 */
    private static boolean containsPaymentKeyword(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.contains("paid") || lower.contains("payment") || lower.contains("purchase")
                || lower.contains("purchased") || lower.contains("refunded") || lower.contains("refund")
                || s.contains("支付") || s.contains("付款") || s.contains("扣款")
                || s.contains("已支付") || s.contains("已付款") || s.contains("退款");
    }

    /** 金额解析：优先匹配前缀货币，再匹配后缀货币，不接受“裸数字” */
    private static Double parseAmount(String content) {
        if (TextUtils.isEmpty(content)) return null;
        Matcher m1 = AMOUNT_PREFIX.matcher(content);
        if (m1.find()) {
            String raw = m1.group(1).replace(",", "");
            try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        }
        Matcher m2 = AMOUNT_SUFFIX.matcher(content);
        if (m2.find()) {
            String raw = m2.group(1).replace(",", "");
            try { return Double.parseDouble(raw); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /** 商家解析：先按关键词 at/to/with/向/给；失败则用“标题去掉金额”兜底 */
    private static String parseMerchant(String title, String text, String big, String lines) {
        String joined = joinNonBlank(" | ", title, text, big, lines);
        String cleaned = AMOUNT_PREFIX.matcher(joined).replaceAll("");
        cleaned = AMOUNT_SUFFIX.matcher(cleaned).replaceAll("");
        cleaned = cleaned.trim();

        for (Pattern p : MERCHANT_HINTS) {
            Matcher m = p.matcher(cleaned);
            if (m.find()) {
                String cand = trimMerchant(m.group(1));
                if (!TextUtils.isEmpty(cand)) return cand;
            }
        }

        String fallback = AMOUNT_PREFIX.matcher(safeString(title)).replaceAll("");
        fallback = AMOUNT_SUFFIX.matcher(fallback).replaceAll("");
        fallback = fallback.trim();
        return !TextUtils.isEmpty(fallback) ? trimMerchant(fallback) : null;
    }

    /** 拿应用名做兜底商家名（取不到则用包名） */
    private String resolveAppLabelOrPkg(String pkg) {
        if (TextUtils.isEmpty(pkg)) return "UnknownApp";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (!TextUtils.isEmpty(label)) return label.toString();
        } catch (Throwable ignore) { }
        return pkg;
    }

    private static String trimMerchant(String s) {
        if (s == null) return null;
        String t = s.replace("|", " ")
                .replace("•", " ")
                .replace("…", " ")
                .trim()
                .replaceAll("\\s+\\-*\\s*$", "");
        return t.length() > 60 ? t.substring(0, 60) : t;
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
