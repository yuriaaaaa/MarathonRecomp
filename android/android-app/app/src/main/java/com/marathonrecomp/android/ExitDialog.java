package com.marathonrecomp.android;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "Exit to the home screen?" confirmation for the back button, matching the WET build's
 * ExitConfirmDialog (charcoal card, amber outline, Keep playing / Exit).
 */
final class ExitDialog extends Dialog {
    private static final int BG = 0xF20B0B10;
    private static final int AMBER = 0xFFF2C14E;
    private static final int AMBER_DIM = 0x66F2C14E;
    private static final int TEXT = 0xFFEDEFF4;
    private static final int TEXT_DIM = 0xFF9AA0AD;
    private static final int INK = 0xFF141006;

    ExitDialog(Context context, Runnable onExit) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(20));
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(dp(26));
        card.setColor(BG);
        card.setStroke(dp(1), AMBER_DIM);
        root.setBackground(card);

        TextView title = new TextView(context);
        title.setText("Exit to the home screen?");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        root.addView(title);

        TextView body = new TextView(context);
        body.setText("The game will close and you'll go back to the launcher. Unsaved progress will be lost.");
        body.setTextColor(TEXT_DIM);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(10);
        root.addView(body, bodyParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(22);

        TextView keep = button(context, "Keep playing", AMBER, pill(Color.TRANSPARENT, AMBER_DIM));
        keep.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        keepParams.setMarginEnd(dp(10));
        buttons.addView(keep, keepParams);

        TextView exit = button(context, "Exit", INK, pill(AMBER, 0));
        exit.setOnClickListener(v -> {
            dismiss();
            onExit.run();
        });
        buttons.addView(exit, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(buttons, buttonsParams);

        setContentView(root);
    }

    @Override
    public void show() {
        super.show();
        Window window = getWindow();
        if (window == null)
            return;
        // Keep the game's immersive mode: otherwise the system bars come back over the game.
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.55f);
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        window.setLayout(Math.min((int) (metrics.widthPixels * 0.80f), dp(480)),
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private TextView button(Context context, String label, int textColor, RippleDrawable background) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(textColor);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private RippleDrawable pill(int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));
        shape.setColor(fill);
        if (stroke != 0)
            shape.setStroke(dp(1), stroke);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics()));
    }
}
