package com.lexflow.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The thin ring around the Done / Close button. It fills clockwise while the button is held,
 * runs back when released early, and flashes faintly on a single tap as a hint to hold.
 */
public class HoldRing extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress;   // 0..1, the filled part
    private float hint;       // 0..1, opacity of the full faint ring
    private ValueAnimator fill;
    private ValueAnimator flash;

    public HoldRing(Context c, AttributeSet a) {
        super(c, a);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3.5f * c.getResources().getDisplayMetrics().density);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    /** Fills the ring over {@code durationMs}, after a short delay so quick taps barely show it. */
    public void start(long delayMs, long durationMs) {
        stopFill();
        fill = ValueAnimator.ofFloat(progress, 1f);
        fill.setStartDelay(delayMs);
        fill.setDuration(durationMs);
        fill.setInterpolator(new LinearInterpolator());
        fill.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        fill.start();
    }

    /** Released before the ring was full: run it back. */
    public void cancel() {
        stopFill();
        fill = ValueAnimator.ofFloat(progress, 0f);
        fill.setDuration(200);
        fill.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        fill.start();
    }

    public void full() {
        stopFill();
        progress = 1f;
        invalidate();
    }

    public void reset() {
        stopFill();
        progress = 0f;
        invalidate();
    }

    /** A single tap: the whole ring glows faintly once. */
    public void flash() {
        if (flash != null) flash.cancel();
        flash = ValueAnimator.ofFloat(0f, 0.55f, 0f);
        flash.setDuration(550);
        flash.addUpdateListener(a -> {
            hint = (float) a.getAnimatedValue();
            invalidate();
        });
        flash.start();
    }

    private void stopFill() {
        if (fill != null) {
            fill.removeAllUpdateListeners();
            fill.cancel();
            fill = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = paint.getStrokeWidth() / 2f + 1f;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        int alpha = paint.getAlpha();
        if (hint > 0f) {
            paint.setAlpha(Math.round(255 * hint));
            canvas.drawArc(box, 0f, 360f, false, paint);
            paint.setAlpha(alpha);
        }
        if (progress > 0f) {
            paint.setAlpha(255);
            canvas.drawArc(box, -90f, 360f * progress, false, paint);
            paint.setAlpha(alpha);
        }
    }
}
