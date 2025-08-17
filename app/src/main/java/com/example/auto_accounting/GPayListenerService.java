package com.example.auto_accounting;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.example.auto_accounting.data.repo.TableWriter;


import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/**
 * 监听所有 App 通知，解析 金额/商家/时间，并通过 TableWriter 写入本地表格。
 */
public class GPayListenerService extends NotificationListenerService {

    private static final String TAG = "GPayListener";

    // ==== 读取范围开关（仅 GPay / 全部 App） ====
    private static final String PREFS = "gpay_listener_prefs";
    private static final String KEY_ONLY_GPAY = "only_gpay"; // true=只读取Google Wallet/GPay; false=读取所有App

    /** Google 钱包/支付 相关包名集合（按地区可能不同，先覆盖常见几种） */
    private static final Set<String> GPAY_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.walletnfcrel", // Google Wallet（多数国家/地区）
            "com.google.android.apps.gmoney",       // 旧版 Google Pay
            "com.google.android.apps.nbu.paisa.user"// Google Pay India
    ));

    /** 读取当前模式（true=只收GPay；false=收所有通知） */
    public static boolean isOnlyGPayMode(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        return sp.getBoolean(KEY_ONLY_GPAY, true);
    }

    /** 设置模式开关 */
    public static void setOnlyGPayMode(Context ctx, boolean onlyGPay) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ONLY_GPAY, onlyGPay)
                .apply();
        Log.i(TAG, "Only-GPay mode set = " + onlyGPay);
    }

    /** 是否为 Google 钱包/支付 的通知 */
    private static boolean isGPayPkg(String pkg) {
        return pkg != null && GPAY_PACKAGES.contains(pkg);
    }

    /** 是否启用 Google Wallet 专用解析（保留旧方法，但优先/仅用本解析） */
    private static final boolean ENABLE_GPAY_WALLET_MODE = false;

    /** Google Wallet 金额样式：AU$xx.xx */
    private static final Pattern GPAY_AU_AMOUNT = Pattern.compile(
            "AU\\$\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    );

    /** 解析结果封装（保留货币符号） */
    private static final class ExtractionResult {
        final String merchant;   // 第一行：商家/描述
        final double amount;     // 第二行：金额（数字）
        final String currency;   // 货币符号，例如 "AU$"
        ExtractionResult(String merchant, double amount, String currency) {
            this.merchant = merchant;
            this.amount = amount;
            this.currency = currency;
        }
    }





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

        // 仅读取 Google Pay/Wallet 通知
        if (!"com.google.android.apps.walletnfcrel".equals(pkg)) {
            Log.d(TAG, "Ignore non-GPay pkg=" + pkg);
            return;
        }

        try {
            Bundle extras = sbn.getNotification() != null ? sbn.getNotification().extras : null;
            if (extras == null) return;

            String title = safeString(extras.getString(Notification.EXTRA_TITLE));
            String text  = safeCharSeq(extras.getCharSequence(Notification.EXTRA_TEXT));
            String big   = safeCharSeq(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));

            String raw = normalize(joinNonBlank(" | ", title, text, big));
            Log.d(TAG, "Raw notify: pkg=" + pkg + " title=" + title + " text=" + text + " big=" + big + " | merged=" + raw);

            // ---------- Google Wallet 专用解析：第一行=商家，第二行=金额（AU$） ----------
            if (ENABLE_GPAY_WALLET_MODE && isGPayPkg(pkg)) {
                ExtractionResult r = extractGPayWallet(extras, title, text, big);
                if (r != null) {
                    long ts = System.currentTimeMillis();
                    Log.i(TAG, "Parsed[GPay] -> amount=" + r.amount + " " + r.currency + ", merchant=" + r.merchant + ", ts=" + ts);

                    // 将金额按“分”写入表，旧逻辑保留但不再走
                    long amountMinor = Math.round(r.amount * 100.0);
                    String description = tidyMerchant(safeString(title));
                    if (TextUtils.isEmpty(description)) description = r.merchant;
                    try {
                        TableWriter.save(getApplicationContext(), ts, description, amountMinor);
                        Log.i(TAG, "Saved to table (GPay) -> amountMinor=" + amountMinor + ", desc=" + description + ", ts=" + ts);
                    } catch (Throwable dbErr) {
                        Log.e(TAG, "Failed to save into table (GPay)", dbErr);
                    }
                    return; // 屏蔽旧的通用解析逻辑
                } else {
                    Log.d(TAG, "GPay style extraction failed; fallback to generic parser.");
                }
            }
            // 对于 Google Pay 包，直接视为支付；其他包再做关键词/金额判定
            if (!isGPayPkg(pkg) && !looksLikePayment(raw)) {
                Log.d(TAG, "Skip non-payment notify. pkg=" + pkg + " raw=" + raw);
                return;
            }

            Double amount = parseAmount(raw);
            String merchant = tidyMerchant(safeString(title));
            if (TextUtils.isEmpty(merchant)) {
                merchant = parseMerchant(title, text, big);
            }

            if (amount == null || TextUtils.isEmpty(merchant)) {
                Log.w(TAG, "Parse failed. raw=" + raw);
                return;
            }

            long ts = System.currentTimeMillis();
            Log.i(TAG, "Parsed -> amount=" + amount + ", merchant=" + merchant + ", ts=" + ts);

            // === 写入本地表格（数据库） ===
            long amountMinor = Math.round(amount ); // 转为“分”
            String description = merchant;                 // 描述字段：商家名
            try {
                TableWriter.save(getApplicationContext(), ts, description, amount);
                Log.i(TAG, "Saved to table -> amountMinor=" + amount + ", desc=" + description + ", ts=" + ts);
            } catch (Throwable dbErr) {
                Log.e(TAG, "Failed to save into table", dbErr);
            }

        } catch (Throwable t) {
            Log.e(TAG, "onNotificationPosted error", t);
        }
    }

    // ================== 解析工具 ==================

    private static boolean looksLikePayment(String s) {
        if (TextUtils.isEmpty(s)) return false;
        // 金额样式（$ / AU$ 等）也视为支付通知
        if (GPAY_AU_AMOUNT.matcher(s).find() || AMOUNT_PATTERN.matcher(s).find()) return true;
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

    /**
     * Google Wallet 通知解析：
     * 需求：
     *   - 第一行完整内容作为商家/描述
     *   - 第二行中提取 "AU$" 的金额数字，保留 "AU$" 作为货币单位
     * 兼容来源：EXTRA_TEXT_LINES / BIG_TEXT / TEXT / TITLE（按优先级取非空行）
     */
    private static ExtractionResult extractGPayWallet(Bundle extras, String title, String text, String big) {
        // 收集所有可能的行（按优先级）
        List<String> lines = new ArrayList<>();
        if (extras != null) {
            CharSequence[] arr = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (arr != null) {
                for (CharSequence cs : arr) {
                    if (cs != null) {
                        String s = normalize(cs.toString());
                        if (!TextUtils.isEmpty(s)) lines.add(s);
                    }
                }
            }
        }
        // 若没有 TEXT_LINES，则尝试从 big / text / title 里按换行切
        if (lines.isEmpty()) {
            String merged = joinNonBlank("\n", safeString(big), safeString(text), safeString(title));
            for (String s : merged.split("\\n")) {
                String t = normalize(s);
                if (!TextUtils.isEmpty(t)) lines.add(t);
            }
        }
        if (lines.isEmpty()) return null;

        String firstLine = lines.get(0); // 商家/描述
        String secondLine = lines.size() > 1 ? lines.get(1) : "";

        // 从第二行提取 AU$ 金额
        Matcher m = GPAY_AU_AMOUNT.matcher(secondLine);
        if (!m.find()) {
            // 有些样式可能把金额放到第一行或第三行，做个保底扫描
            for (int i = 0; i < lines.size(); i++) {
                m = GPAY_AU_AMOUNT.matcher(lines.get(i));
                if (m.find()) {
                    // 若不是第二行，也依然使用第一行作为商家
                    break;
                }
            }
        }
        if (m == null || !m.find(0)) {
            return null; // 未找到 AU$ 金额，视为不符合该样式
        }

        String num = m.group(1).replace(",", "");
        double amount;
        try {
            amount = Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return null;
        }
        return new ExtractionResult(firstLine, amount, "AU$");
    }
}