package com.puzzlehub.sudoku.ui.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import com.puzzlehub.sudoku.game.R;

public final class AppNotices {
    private AppNotices() {}

    public static void show(View view, CharSequence message, boolean error, boolean brief) {
        render(view, message, error, brief, false);
    }

    public static void success(View view, CharSequence message) {
        if (!CompactSuccessNotice.show(view, message)) {
            render(view, message, false, true, true);
        }
    }

    private static void render(View source, CharSequence message, boolean error, boolean brief, boolean success) {
        int duration = Snackbar.LENGTH_LONG;
        if (brief) {
            duration = Snackbar.LENGTH_SHORT;
        }
        Snackbar snackbar = Snackbar.make(source, message, duration);
        Context context = source.getContext();
        int accent = R.color.primary;
        int drawable = R.drawable.ic_notice_info;
        if (error) {
            accent = R.color.error;
        } else if (success) {
            accent = R.color.mint_ink;
            drawable = R.drawable.ic_notice_success;
        }
        View layout = snackbar.getView();
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color(context, R.color.notice_top), color(context, R.color.notice_bottom)});
        surface.setCornerRadius(dp(context, 22));
        surface.setStroke(dp(context, 1), color(context, R.color.glass_rim));
        layout.setBackground(surface);
        layout.setElevation(0f);
        if (layout.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
            margins.leftMargin = dp(context, 16);
            margins.rightMargin = dp(context, 16);
            margins.bottomMargin = dp(context, 12);
            layout.setLayoutParams(margins);
        }
        TextView text = layout.findViewById(com.google.android.material.R.id.snackbar_text);
        text.setTextColor(color(context, R.color.ink));
        text.setTextSize(13f);
        text.setMaxLines(4);
        text.setCompoundDrawablePadding(dp(context, 10));
        Drawable icon = ContextCompat.getDrawable(context, drawable);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color(context, accent));
            icon.setBounds(0, 0, dp(context, 22), dp(context, 22));
        }
        text.setCompoundDrawablesRelative(icon, null, null, null);
        View navigation = source.getRootView().findViewById(R.id.navigationCard);
        if (navigation != null && navigation.isShown()) {
            snackbar.setAnchorView(navigation);
        }
        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);
        snackbar.show();
    }

    private static int color(Context context, int resource) { return ContextCompat.getColor(context, resource); }
    private static int dp(Context context, int value) { return (int) (value * context.getResources().getDisplayMetrics().density); }
}
