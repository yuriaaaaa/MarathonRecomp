package com.marathonrecomp.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-screen Xbox 360 style controller, ported from the WET build's VirtualGamepadView:
 * translucent thin-outlined controls, coloured ABXY, two sticks, D-pad, bumpers, triggers,
 * Back and Start. Its state goes to an SDL virtual controller in the game (native side:
 * os/android/touch_controls_android.cpp), so the game treats it like any other pad.
 *
 * Layout (fractions of the screen, landscape): left stick bottom-left, D-pad and right stick
 * across the middle, ABXY bottom-right, LB/RB at the top corners, LT/RT on the side edges,
 * Back/Start bottom-centre. Real multi-touch: each finger owns the control it went down on;
 * the D-pad follows the finger in 8 directions.
 */
public class TouchControlsView extends View {
    // Button bits, in SDL_CONTROLLER_BUTTON_* order (the virtual controller maps button i to it).
    static final int BIT_A = 0, BIT_B = 1, BIT_X = 2, BIT_Y = 3, BIT_BACK = 4, BIT_GUIDE = 5, BIT_START = 6;
    static final int BIT_LS = 7, BIT_RS = 8, BIT_LB = 9, BIT_RB = 10;
    static final int BIT_DUP = 11, BIT_DDOWN = 12, BIT_DLEFT = 13, BIT_DRIGHT = 14;
    static final int BIT_LT = 15, BIT_RT = 16; // triggers go through axes

    static final float MIN_OPACITY = 0.15f, MAX_OPACITY = 1.0f;
    static final float MIN_SCALE = 0.7f, MAX_SCALE = 1.6f;

    static native void nativeConfigure(boolean enabled, boolean rumble);

    static native void nativeSetRumble(boolean rumble);

    static native void nativeSetState(int buttons, int lx, int ly, int rx, int ry, int lt, int rt);

    private static volatile Vibrator sVibrator;
    private static volatile boolean sNativeReady;

    /** Called by the game (any thread) when it rumbles the touch controller. */
    @SuppressWarnings("unused")
    static void onRumble(int low, int high) {
        Vibrator vibrator = sVibrator;
        if (vibrator == null)
            return;
        int amplitude = Math.max(low, high) >> 8;
        try {
            if (amplitude <= 0)
                vibrator.cancel();
            else
                vibrator.vibrate(VibrationEffect.createOneShot(5000, Math.max(1, Math.min(255, amplitude))));
        } catch (Exception ignored) {
        }
    }

    /** Tells the game whether to create the touch controller; call before the game starts. */
    static void configure(Context context, boolean attach, boolean rumble) {
        sVibrator = vibrator(context);
        try {
            nativeConfigure(attach, rumble);
            sNativeReady = true;
        } catch (UnsatisfiedLinkError e) {
            sNativeReady = false;
        }
    }

    static void setRumble(boolean rumble) {
        if (!sNativeReady)
            return;
        nativeSetRumble(rumble);
        if (!rumble && sVibrator != null)
            sVibrator.cancel();
    }

    static void stopRumble() {
        Vibrator vibrator = sVibrator;
        if (vibrator != null)
            vibrator.cancel();
    }

    private static Vibrator vibrator(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return manager != null ? manager.getDefaultVibrator() : null;
            }
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ model

    private enum Kind { BUTTON, STICK, DPAD, TRIGGER }

    private static final class Pad {
        final String id;
        final Kind kind;
        final float cx, cy;   // fractions of width / height
        final float radius;   // pixels
        final int bit;

        Pad(String id, Kind kind, float cx, float cy, float radius, int bit) {
            this.id = id;
            this.kind = kind;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.bit = bit;
        }
    }

    private static final class StickTouch {
        final int pointerId;
        float centerX, centerY, radiusPx;
        float thumbX, thumbY;

        StickTouch(int pointerId, float centerX, float centerY, float radiusPx) {
            this.pointerId = pointerId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.radiusPx = radiusPx;
        }
    }

    private static final class Assignment {
        final Pad pad;
        int mask;
        final int trigger;

        Assignment(Pad pad, int mask, int trigger) {
            this.pad = pad;
            this.mask = mask;
            this.trigger = trigger;
        }
    }

    private float opacity;
    private float scale;
    private boolean haptics;
    private boolean faded; // a physical controller is in use: draw nothing until touched

    private List<Pad> controls = new ArrayList<>();
    private float viewW = 1, viewH = 1;

    private final int[] buttons = new int[32];
    private int lt, rt, lx, ly, rx, ry;
    private StickTouch stickL, stickR;
    private final Map<Integer, Assignment> assignments = new HashMap<>();

    private int lastMask = -1, lastLx, lastLy, lastRx, lastRy, lastLt, lastRt;

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Map<String, Integer> faceColors = new HashMap<>();

    public TouchControlsView(Context context, ControlsSettings settings) {
        super(context);
        opacity = clamp(settings.opacity, MIN_OPACITY, MAX_OPACITY);
        scale = clamp(settings.scale, MIN_SCALE, MAX_SCALE);
        haptics = settings.buttonHaptics;

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        strokePaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        faceColors.put("A", Color.argb(255, 0x7A, 0xC7, 0x6E));
        faceColors.put("B", Color.argb(255, 0xE0, 0x6C, 0x62));
        faceColors.put("X", Color.argb(255, 0x5A, 0x9E, 0xD6));
        faceColors.put("Y", Color.argb(255, 0xE8, 0xC8, 0x5A));
    }

    private List<Pad> layout(int width, int height) {
        float small = Math.min(width, height) * scale;
        List<Pad> out = new ArrayList<>();
        out.add(new Pad("A", Kind.BUTTON, 0.885f, 0.80f, 0.075f * small, BIT_A));
        out.add(new Pad("B", Kind.BUTTON, 0.940f, 0.68f, 0.075f * small, BIT_B));
        out.add(new Pad("X", Kind.BUTTON, 0.830f, 0.68f, 0.075f * small, BIT_X));
        out.add(new Pad("Y", Kind.BUTTON, 0.885f, 0.56f, 0.075f * small, BIT_Y));
        out.add(new Pad("LB", Kind.BUTTON, 0.115f, 0.10f, 0.065f * small, BIT_LB));
        out.add(new Pad("RB", Kind.BUTTON, 0.885f, 0.10f, 0.065f * small, BIT_RB));
        out.add(new Pad("LT", Kind.TRIGGER, 0.045f, 0.28f, 0.065f * small, BIT_LT));
        out.add(new Pad("RT", Kind.TRIGGER, 0.955f, 0.28f, 0.065f * small, BIT_RT));
        out.add(new Pad("BACK", Kind.BUTTON, 0.425f, 0.86f, 0.048f * small, BIT_BACK));
        out.add(new Pad("START", Kind.BUTTON, 0.575f, 0.86f, 0.048f * small, BIT_START));
        out.add(new Pad("STICK", Kind.STICK, 0.135f, 0.66f, 0.145f * small, BIT_LS));
        out.add(new Pad("RSTICK", Kind.STICK, 0.615f, 0.60f, 0.145f * small, BIT_RS));
        out.add(new Pad("DPAD", Kind.DPAD, 0.385f, 0.62f, 0.105f * small, BIT_DUP));
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewW = Math.max(1, w);
        viewH = Math.max(1, h);
        controls = layout(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faded)
            return;
        float base = clamp(opacity, MIN_OPACITY, MAX_OPACITY);
        for (Pad pad : controls) {
            switch (pad.kind) {
                case STICK:
                    drawStick(canvas, pad, pad.id.equals("STICK") ? stickL : stickR, base);
                    break;
                case DPAD:
                    drawDpad(canvas, pad.cx * viewW, pad.cy * viewH, pad.radius, base);
                    break;
                default:
                    drawButton(canvas, pad, base);
                    break;
            }
        }
    }

    private void drawButton(Canvas canvas, Pad pad, float base) {
        float cx = pad.cx * viewW, cy = pad.cy * viewH, r = pad.radius;
        boolean pressed = pad.id.equals("LT") ? lt > 0 : pad.id.equals("RT") ? rt > 0 : buttons[pad.bit] != 0;
        float a = pressed ? Math.max(base, 0.35f) : base * 0.55f;

        if (pad.kind == Kind.TRIGGER) {
            fillPaint.setColor(Color.WHITE);
            fillPaint.setAlpha((int) (a * 255 * 0.30f));
            float fraction = (pad.id.equals("LT") ? lt : rt) / 32767f;
            rect.set(cx - r, cy + r - 2 * r * fraction, cx + r, cy + r);
            canvas.drawRoundRect(rect, r, r, fillPaint);
            strokePaint.setColor(Color.WHITE);
            strokePaint.setAlpha((int) (a * 255));
            rect.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawRoundRect(rect, r, r, strokePaint);
            label(canvas, pad.id, cx, cy, r * 0.62f, Color.WHITE, base);
            return;
        }

        Integer tint = faceColors.get(pad.id);
        int colour = tint != null ? tint : Color.WHITE;
        fillPaint.setColor(colour);
        fillPaint.setAlpha((int) (pressed ? a * 255 * 0.55f : a * 255 * 0.16f));
        canvas.drawCircle(cx, cy, r, fillPaint);
        strokePaint.setColor(colour);
        strokePaint.setAlpha((int) (a * 255));
        canvas.drawCircle(cx, cy, r, strokePaint);

        String text = pad.id.equals("BACK") ? "\u25C0" : pad.id.equals("START") ? "\u25B6" : pad.id;
        label(canvas, text, cx, cy, r * (pad.id.length() > 1 ? 0.62f : 0.95f), colour, base);
    }

    private void label(Canvas canvas, String text, float cx, float cy, float size, int colour, float base) {
        textPaint.setColor(colour);
        textPaint.setAlpha((int) (base * 235));
        textPaint.setTextSize(size);
        canvas.drawText(text, cx, cy + size * 0.35f, textPaint);
    }

    private void drawStick(Canvas canvas, Pad pad, StickTouch touch, float base) {
        float cx = pad.cx * viewW, cy = pad.cy * viewH, r = pad.radius;
        strokePaint.setColor(Color.WHITE);
        strokePaint.setAlpha((int) (base * 150));
        canvas.drawCircle(cx, cy, r, strokePaint);
        strokePaint.setAlpha((int) (base * 60));
        canvas.drawCircle(cx, cy, r * 0.62f, strokePaint);
        textPaint.setColor(Color.WHITE);
        textPaint.setAlpha((int) (base * 110));
        textPaint.setTextSize(r * 0.34f);
        canvas.drawText(pad.id.equals("STICK") ? "L" : "R", cx, cy + r * 0.12f, textPaint);
        float tx = cx + (touch != null ? touch.thumbX : 0);
        float ty = cy + (touch != null ? touch.thumbY : 0);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setAlpha((int) (base * 90));
        canvas.drawCircle(tx, ty, r * 0.42f, fillPaint);
        strokePaint.setAlpha((int) (base * 200));
        canvas.drawCircle(tx, ty, r * 0.42f, strokePaint);
    }

    private void drawDpad(Canvas canvas, float cx, float cy, float r, float base) {
        float a = base * 170, armW = r * 0.34f;
        rect.set(cx - armW, cy - r, cx + armW, cy - r * 0.15f);
        drawDirection(canvas, buttons[BIT_DUP] != 0, a);
        rect.set(cx - armW, cy + r * 0.15f, cx + armW, cy + r);
        drawDirection(canvas, buttons[BIT_DDOWN] != 0, a);
        rect.set(cx - r, cy - armW, cx - r * 0.15f, cy + armW);
        drawDirection(canvas, buttons[BIT_DLEFT] != 0, a);
        rect.set(cx + r * 0.15f, cy - armW, cx + r, cy + armW);
        drawDirection(canvas, buttons[BIT_DRIGHT] != 0, a);
    }

    private void drawDirection(Canvas canvas, boolean pressed, float alpha) {
        fillPaint.setColor(Color.WHITE);
        fillPaint.setAlpha((int) (pressed ? alpha * 0.8f : alpha * 0.22f));
        canvas.drawRoundRect(rect, rect.width() / 2.2f, rect.height() / 2.2f, fillPaint);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (faded) {
            faded = false;
            invalidate();
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                assign(event.getPointerId(index), event.getX(index), event.getY(index));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++)
                    move(event.getPointerId(i), event.getX(i), event.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                release(event.getPointerId(event.getActionIndex()));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
        }
        return true;
    }

    private void assign(int pointerId, float x, float y) {
        // Sticks first (largest areas).
        for (Pad pad : controls) {
            if (pad.kind != Kind.STICK)
                continue;
            boolean left = pad.id.equals("STICK");
            if (left ? stickL != null : stickR != null)
                continue;
            float cx = pad.cx * viewW, cy = pad.cy * viewH;
            if (Math.hypot(x - cx, y - cy) <= pad.radius * 1.25f) {
                StickTouch touch = new StickTouch(pointerId, cx, cy, pad.radius * 0.62f);
                if (left)
                    stickL = touch;
                else
                    stickR = touch;
                tap();
                move(pointerId, x, y);
                return;
            }
        }
        // Otherwise the nearest control within reach.
        Pad best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Pad pad : controls) {
            if (pad.kind == Kind.STICK)
                continue;
            double d = Math.hypot(x - pad.cx * viewW, y - pad.cy * viewH);
            double reach = pad.radius * (pad.kind == Kind.DPAD ? 1.35f : 1.45f);
            if (d <= reach && d < bestDistance) {
                best = pad;
                bestDistance = d;
            }
        }
        if (best == null)
            return;
        int mask = best.kind == Kind.DPAD ? dpadMask(best, x, y) : 1 << best.bit;
        int trigger = best.kind == Kind.TRIGGER ? best.bit : 0;
        assignments.put(pointerId, new Assignment(best, mask, trigger));
        if (trigger == BIT_LT)
            lt = 32767;
        if (trigger == BIT_RT)
            rt = 32767;
        syncButtons();
        tap();
        send(false);
        invalidate();
    }

    /** 8-way D-pad: the octant of the touch around the centre; diagonals press two directions. */
    private int dpadMask(Pad pad, float x, float y) {
        double angle = Math.toDegrees(Math.atan2(y - pad.cy * viewH, x - pad.cx * viewW));
        if (angle >= 157.5 || angle < -157.5) return 1 << BIT_DLEFT;
        if (angle >= 112.5) return (1 << BIT_DLEFT) | (1 << BIT_DDOWN);
        if (angle >= 67.5) return 1 << BIT_DDOWN;
        if (angle >= 22.5) return (1 << BIT_DDOWN) | (1 << BIT_DRIGHT);
        if (angle >= -22.5) return 1 << BIT_DRIGHT;
        if (angle >= -67.5) return (1 << BIT_DRIGHT) | (1 << BIT_DUP);
        if (angle >= -112.5) return 1 << BIT_DUP;
        return (1 << BIT_DUP) | (1 << BIT_DLEFT);
    }

    private void move(int pointerId, float x, float y) {
        StickTouch touch = stickL != null && stickL.pointerId == pointerId ? stickL
                : stickR != null && stickR.pointerId == pointerId ? stickR : null;
        if (touch != null) {
            float dx = x - touch.centerX, dy = y - touch.centerY;
            float length = (float) Math.hypot(dx, dy);
            if (length > touch.radiusPx) {
                dx = dx / length * touch.radiusPx;
                dy = dy / length * touch.radiusPx;
            }
            touch.thumbX = dx;
            touch.thumbY = dy;
            int ax = clampAxis(dx / touch.radiusPx * 32767f);
            int ay = clampAxis(dy / touch.radiusPx * 32767f);
            if (touch == stickL) {
                lx = ax;
                ly = ay;
            } else {
                rx = ax;
                ry = ay;
            }
            send(false);
            invalidate();
            return;
        }
        Assignment assignment = assignments.get(pointerId);
        if (assignment == null || assignment.pad.kind != Kind.DPAD)
            return;
        int mask = dpadMask(assignment.pad, x, y);
        if (mask != assignment.mask) {
            assignment.mask = mask;
            syncButtons();
            tap();
            send(false);
            invalidate();
        }
    }

    private void release(int pointerId) {
        if (stickL != null && stickL.pointerId == pointerId) {
            stickL = null;
            lx = ly = 0;
            send(false);
            invalidate();
            return;
        }
        if (stickR != null && stickR.pointerId == pointerId) {
            stickR = null;
            rx = ry = 0;
            send(false);
            invalidate();
            return;
        }
        Assignment assignment = assignments.remove(pointerId);
        if (assignment == null)
            return;
        if (assignment.trigger == BIT_LT && !holdsTrigger(BIT_LT))
            lt = 0;
        if (assignment.trigger == BIT_RT && !holdsTrigger(BIT_RT))
            rt = 0;
        syncButtons();
        send(false);
        invalidate();
    }

    private boolean holdsTrigger(int trigger) {
        for (Assignment a : assignments.values())
            if (a.trigger == trigger)
                return true;
        return false;
    }

    void releaseAll() {
        stickL = stickR = null;
        assignments.clear();
        lx = ly = rx = ry = lt = rt = 0;
        for (int i = 0; i < buttons.length; i++)
            buttons[i] = 0;
        send(true);
        invalidate();
    }

    /** Button bits are the union of every finger's mask (two fingers on one button keep it held). */
    private void syncButtons() {
        int union = 0;
        for (Assignment a : assignments.values())
            union |= a.mask;
        for (int bit = 0; bit < 15; bit++)
            if (bit != BIT_LS && bit != BIT_RS)
                buttons[bit] = (union & (1 << bit)) != 0 ? 1 : 0;
    }

    /** Sends the state to the game when it changed. */
    private void send(boolean force) {
        if (!sNativeReady)
            return;
        int mask = 0;
        for (int i = 0; i < 15; i++)
            if (buttons[i] != 0)
                mask |= 1 << i;
        if (!force && mask == lastMask && lx == lastLx && ly == lastLy && rx == lastRx && ry == lastRy
                && lt == lastLt && rt == lastRt)
            return;
        lastMask = mask;
        lastLx = lx;
        lastLy = ly;
        lastRx = rx;
        lastRy = ry;
        lastLt = lt;
        lastRt = rt;
        nativeSetState(mask, lx, ly, rx, ry, lt, rt);
    }

    private void tap() {
        if (!haptics)
            return;
        Vibrator vibrator = sVibrator;
        if (vibrator == null)
            return;
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ live settings

    void setOpacity(float value) {
        opacity = clamp(value, MIN_OPACITY, MAX_OPACITY);
        invalidate();
    }

    void setScale(float value) {
        scale = clamp(value, MIN_SCALE, MAX_SCALE);
        releaseAll();
        controls = layout((int) viewW, (int) viewH);
        invalidate();
    }

    void setHaptics(boolean value) {
        haptics = value;
    }

    /** A physical controller is being used: hide until the screen is touched again. */
    void fade() {
        if (faded)
            return;
        faded = true;
        releaseAll();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampAxis(float value) {
        return (int) Math.max(-32768, Math.min(32767, value));
    }
}
