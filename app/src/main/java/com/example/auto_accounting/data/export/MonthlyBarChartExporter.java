package com.example.auto_accounting.data.export;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;

import com.example.auto_accounting.data.db.DbProvider;
import com.example.auto_accounting.data.db.Table;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Builds and exports a per-day summed bar chart for a month.
 * Drawing is done on a {@link Bitmap} via {@link Canvas}.
 */
public final class MonthlyBarChartExporter {

    // Layout constants (no magic numbers).
    private static final int WIDTH = 1400;
    private static final int HEIGHT = 900;
    private static final int MARGIN_LEFT = 100;
    private static final int MARGIN_RIGHT = 40;
    private static final int MARGIN_TOP = 60;
    private static final int MARGIN_BOTTOM = 140;
    private static final float BAR_GAP = 8f;
    private static final int Y_TICKS = 5;

    private MonthlyBarChartExporter() {
        // Utility class.
    }

    /** Exports the current month (system timezone) as a PNG to the given Uri. */
    public static void exportCurrentMonth(
            Context context,
            ContentResolver resolver,
            Uri uri
    ) throws IOException {
        YearMonth ym = YearMonth.now(ZoneId.systemDefault());
        exportMonth(context, resolver, uri, ym.getYear(), ym.getMonthValue());
    }

    /** Exports a specific year-month as a PNG to the given Uri. */
    public static void exportMonth(
            Context context,
            ContentResolver resolver,
            Uri uri,
            int year,
            int month
    ) throws IOException {
        YearMonth ym = YearMonth.of(year, month);
        ZoneId zone = ZoneId.systemDefault();

        // [1] Compute time range [start, end).
        long start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = ym.plusMonths(1).atDay(1).atStartOfDay(zone)
                .toInstant().toEpochMilli();

        // [2] Load rows within the month.
        List<Table> rows = DbProvider.get(context).tableDao().listInRange(start, end);

        // [3] Aggregate to daily sums (cents → currency units).
        double[] daySums = buildDaySums(rows, ym, zone);

        // [4] Draw and write to Uri.
        Bitmap bmp = drawChartBitmap(daySums, year, month);
        writeToUri(resolver, uri, bmp);
        bmp.recycle();
    }

    /** Builds a chart bitmap for the current month (for in-app preview). */
    public static Bitmap buildCurrentMonthBitmap(Context context) throws IOException {
        YearMonth ym = YearMonth.now(ZoneId.systemDefault());
        return buildMonthBitmap(context, ym.getYear(), ym.getMonthValue());
    }

    /** Builds a chart bitmap for a specific year-month (for in-app preview). */
    public static Bitmap buildMonthBitmap(Context context, int year, int month) throws IOException {
        YearMonth ym = YearMonth.of(year, month);
        ZoneId zone = ZoneId.systemDefault();

        long start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();

        List<Table> rows = DbProvider.get(context).tableDao().listInRange(start, end);
        double[] daySums = buildDaySums(rows, ym, zone);
        return drawChartBitmap(daySums, year, month);
    }

    /** Aggregates DB rows into per-day sums for the given month. */
    private static double[] buildDaySums(List<Table> rows, YearMonth ym, ZoneId zone) {
        int days = ym.lengthOfMonth();
        double[] daySum = new double[days];

        for (Table row : rows) {
            LocalDate d = Instant.ofEpochMilli(row.timeMillis).atZone(zone).toLocalDate();
            if (d.getYear() == ym.getYear() && d.getMonthValue() == ym.getMonthValue()) {
                int idx = d.getDayOfMonth() - 1;
                daySum[idx] += row.amountMinor / 100.0; // cents → currency units
            }
        }
        return daySum;
    }

    /** Draws the bar chart into a bitmap and returns it. */
    private static Bitmap drawChartBitmap(double[] daySum, int year, int month) {
        int plotW = WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
        int plotH = HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;

        double maxY = 0;
        for (double v : daySum) if (v > maxY) maxY = v;
        if (maxY <= 0) maxY = 1;

        Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFF333333);
        text.setTextSize(32);

        Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
        axis.setColor(0xFF333333);
        axis.setStrokeWidth(2);

        Paint grid = new Paint(axis);
        grid.setColor(0xFFEEEEEE);
        grid.setStrokeWidth(1);

        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(0xFF4CAF50);

        // Background and title
        canvas.drawColor(0xFFFFFFFF);
        text.setTextSize(42);
        canvas.drawText(year + " / " + month + " Spending (Daily Sum)", MARGIN_LEFT, 48, text);
        text.setTextSize(28);

        // Axes
        int x0 = MARGIN_LEFT;
        int y0 = HEIGHT - MARGIN_BOTTOM;
        canvas.drawLine(x0, y0, WIDTH - MARGIN_RIGHT, y0, axis);
        canvas.drawLine(x0, y0, x0, MARGIN_TOP, axis);

        // Y ticks & grid
        DecimalFormat df = new DecimalFormat("0.##");
        for (int i = 1; i <= Y_TICKS; i++) {
            float y = (float) (y0 - (i * 1.0 / Y_TICKS) * plotH);
            canvas.drawLine(x0, y, WIDTH - MARGIN_RIGHT, y, grid);
            String label = df.format(maxY * i / Y_TICKS);
            float labelX = x0 - 12 - text.measureText(label);
            canvas.drawText(label, labelX, y + 10, text);
        }
        canvas.drawText("Unit: currency", WIDTH - MARGIN_RIGHT - 160, MARGIN_TOP + 8, text);

        // Bars & X labels
        int days = daySum.length;
        float barW = Math.max(8f, (plotW - BAR_GAP * (days + 1)) / days);
        int step = Math.max(1, (int) Math.ceil(days / 15.0)); // up to ~15 labels

        for (int i = 0; i < days; i++) {
            float left = MARGIN_LEFT + BAR_GAP + i * (barW + BAR_GAP);
            float top = (float) (y0 - (daySum[i] / maxY) * plotH);
            RectF r = new RectF(left, top, left + barW, y0);
            canvas.drawRect(r, bar);

            int day = i + 1;
            if (i % step == 0 || i == days - 1) {
                String label = String.valueOf(day);
                float tw = text.measureText(label);
                canvas.drawText(label, left + (barW - tw) / 2f, y0 + 36, text);
            }
        }

        return bmp;
    }

    /** Writes the bitmap as PNG to the given Uri. */
    private static void writeToUri(ContentResolver resolver, Uri uri, Bitmap bmp) throws IOException {
        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) throw new IOException("openOutputStream returned null: " + uri);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
        }
    }
}


