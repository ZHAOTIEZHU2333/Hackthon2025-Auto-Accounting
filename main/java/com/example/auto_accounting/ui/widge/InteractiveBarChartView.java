package com.example.auto_accounting.ui.widge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

/** 简易可点击的柱状图：支持按天显示、点击柱子回调当天索引（1..days）。 */
public class InteractiveBarChartView extends View {

    /** 每日合计（单位：分，允许为 0） */
    private List<Long> dayTotalsMinor; // 长度 = daysInMonth
    private int daysInMonth = 30;      // 默认 30，设置数据时覆盖
    private OnBarClickListener listener;

    // 画笔
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 内边距/坐标
    private float contentLeft, contentRight, contentTop, contentBottom;
    private final RectF barRect = new RectF();

    public InteractiveBarChartView(Context c) { this(c, null); }
    public InteractiveBarChartView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }

    private void init() {
        barPaint.setColor(0xFF4CAF50);  // 绿色柱子
        axisPaint.setColor(0xFFB0BEC5); // 灰色坐标
        axisPaint.setStrokeWidth(2f);
        setClickable(true);
    }

    /** 设置数据： totalsMinor 为每天合计（分），days 为当月天数 */
    public void setData(List<Long> totalsMinor, int days) {
        this.dayTotalsMinor = totalsMinor;
        this.daysInMonth = Math.max(1, days);
        invalidate();
    }

    /** 点击回调接口 */
    public interface OnBarClickListener {
        /** @param day 1..daysInMonth */
        void onBarClick(int day);
    }

    public void setOnBarClickListener(@Nullable OnBarClickListener l) {
        this.listener = l;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float pad = dp(12);
        contentLeft = pad;
        contentRight = w - pad;
        contentTop = pad;
        contentBottom = h - pad - dp(8); // 预留底部
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        // x 轴
        c.drawLine(contentLeft, contentBottom, contentRight, contentBottom, axisPaint);

        if (dayTotalsMinor == null || dayTotalsMinor.isEmpty()) return;

        float contentW = contentRight - contentLeft;
        float barW = contentW / daysInMonth;
        // 最大值（避免除零）
        long maxMinor = 0;
        for (Long v : dayTotalsMinor) if (v != null && v > maxMinor) maxMinor = v;
        if (maxMinor <= 0) maxMinor = 1;

        for (int i = 0; i < daysInMonth; i++) {
            long v = (i < dayTotalsMinor.size() && dayTotalsMinor.get(i) != null) ? dayTotalsMinor.get(i) : 0;
            float ratio = v * 1f / maxMinor;
            float barH = (contentBottom - contentTop) * ratio;

            float left = contentLeft + i * barW + dp(2);
            float right = contentLeft + (i + 1) * barW - dp(2);
            float top = contentBottom - barH;

            barRect.set(left, top, right, contentBottom);
            c.drawRect(barRect, barPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && listener != null) {
            int day = pickDayByX(e.getX());
            if (day >= 1 && day <= daysInMonth) listener.onBarClick(day);
        }
        return super.onTouchEvent(e) || isClickable();
    }

    /** 根据触点 X 计算 day（1..daysInMonth） */
    private int pickDayByX(float x) {
        float clamped = Math.max(contentLeft, Math.min(x, contentRight));
        float barW = (contentRight - contentLeft) / daysInMonth;
        int idx = (int) ((clamped - contentLeft) / barW);
        return idx + 1;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}

