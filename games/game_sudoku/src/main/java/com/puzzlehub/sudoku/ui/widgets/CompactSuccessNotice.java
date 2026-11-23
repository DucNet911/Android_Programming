package com.puzzlehub.sudoku.ui.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.game.R;

final class CompactSuccessNotice {
    private static final String TAG = "compact-success-notice";

    private CompactSuccessNotice() {}

    static boolean show(View source, CharSequence message) {
        if (!source.isAttachedToWindow()) {
            return false;
        }
        View root = source.getRootView().findViewById(android.R.id.content);
        if (!(root instanceof FrameLayout container) || container.getWidth() <= dp(source.getContext(), 28)) {
            return false;
        }
        View previous = container.findViewWithTag(TAG);
        if (previous != null) {
            container.removeView(previous);
        }

        Context context = source.getContext();
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color(context, R.color.notice_top), color(context, R.color.notice_bottom)});
        surface.setCornerRadius(dp(context, 18));
        surface.setStroke(dp(context, 1), color(context, R.color.glass_rim));

        TextView pill = new TextView(context);
        pill.setId(com.google.android.material.R.id.snackbar_text);
        pill.setTag(TAG);
        pill.setText(message);
        pill.setTextColor(color(context, R.color.ink));
        pill.setTextSize(13);
        pill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setMaxLines(2);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setMaxWidth(Math.min(dp(context, 300), container.getWidth() - dp(context, 28)));
        pill.setPaddingRelative(dp(context, 13), dp(context, 9), dp(context, 15), dp(context, 9));
        pill.setCompoundDrawablePadding(dp(context, 8));
        pill.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        pill.setBackground(surface);
        pill.setElevation(dp(context, 5));

        Drawable tick = ContextCompat.getDrawable(context, R.drawable.ic_notice_success);
        if (tick != null) {
            tick = tick.mutate();
            tick.setBounds(0, 0, dp(context, 19), dp(context, 19));
            tick.setTint(color(context, R.color.mint_ink));
            pill.setCompoundDrawablesRelative(tick, null, null, null);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = bottomMargin(container);
        container.addView(pill, params);
        pill.postDelayed(() -> container.removeView(pill), 1850L);
        return true;
    }

    private static int bottomMargin(FrameLayout container) {
        int margin = dp(container.getContext(), 18);
        View navigation = container.findViewById(R.id.navigationCard);
        if (navigation != null && navigation.isShown()) {
            Rect frame = new Rect();
            Rect bar = new Rect();
            if (container.getGlobalVisibleRect(frame) && navigation.getGlobalVisibleRect(bar)) {
                margin = Math.max(margin, frame.bottom - bar.top + dp(container.getContext(), 9));
            }
        }
        return margin;
    }

    private static int color(Context context, int resource) {
        return ContextCompat.getColor(context, resource);
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
