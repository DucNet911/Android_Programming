package com.puzzlehub.sudoku.ui.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.card.MaterialCardView;
import com.puzzlehub.sudoku.game.R;

public final class ActionCardView extends MaterialCardView {
    private final LinearLayout row;
    private final FrameLayout badge;
    private final ImageView icon;
    private final TextView symbol;
    private final TextView titleView;
    private final TextView detailView;
    private final TextView tail;

    public ActionCardView(Context context) { this(context, null); }

    public ActionCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setRadius(dp(22));
        setCardElevation(0f);
        setStrokeWidth(dp(1));
        setStrokeColor(color(R.color.glass_rim));
        setClickable(true);
        setFocusable(true);
        row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new FrameLayout.LayoutParams(-1, -1));
        badge = new FrameLayout(context);
        icon = new ImageView(context);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        symbol = new TextView(context);
        symbol.setGravity(Gravity.CENTER);
        symbol.setTextSize(22f);
        symbol.setTypeface(Typeface.DEFAULT_BOLD);
        symbol.setVisibility(GONE);
        badge.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
        row.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(color(R.color.ink));
        titleView.setMaxLines(2);
        detailView = new TextView(context);
        detailView.setTextColor(color(R.color.muted));
        detailView.setMaxLines(2);
        labels.addView(titleView);
        labels.addView(detailView);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = dp(12);
        row.addView(labels, labelParams);

        tail = new TextView(context);
        tail.setTextColor(color(R.color.muted));
        tail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tailParams = new LinearLayout.LayoutParams(-2, -2);
        tailParams.leftMargin = dp(8);
        row.addView(tail, tailParams);
        for (View child : new View[]{icon, symbol, titleView, detailView, tail}) {
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    public void configure(String title, String detail, int iconResource, int tint, int accent) {
        configure(title, detail, iconResource, tint, accent, false, null);
    }

    public void configure(String title, String detail, int iconResource, int tint, int accent,
                          boolean compact, @Nullable String trailing) {
        int start = 14;
        int badgeSize = dp(46);
        int iconSize = dp(26);
        float titleSize = 16;
        if (compact) {
            start = 10;
            badgeSize = dp(30);
            iconSize = dp(20);
            titleSize = 14;
        }
        row.setPadding(dp(start), dp(4), dp(14), dp(4));
        titleView.setText(title);
        titleView.setTextSize(titleSize);
        detailView.setText(detail);
        detailView.setTextSize(11f);
        detailView.setPadding(0, dp(3), 0, 0);
        if (detail.trim().isEmpty()) {
            detailView.setVisibility(GONE);
        } else {
            detailView.setVisibility(VISIBLE);
        }
        icon.setVisibility(VISIBLE);
        symbol.setVisibility(GONE);
        icon.setImageResource(iconResource);
        if (iconResource == R.drawable.ic_result_win || iconResource == R.drawable.ic_sudoku) {
            icon.clearColorFilter();
        } else {
            icon.setColorFilter(color(accent));
        }
        resizeSquare(badge, badgeSize);
        resizeSquare(icon, iconSize);
        styleBadge(color(tint));
        styleRow(ColorUtils.blendARGB(color(tint), color(R.color.surface), 0.55f));

        StringBuilder accessibility = new StringBuilder(title);
        if (!detail.trim().isEmpty()) {
            accessibility.append(". ").append(detail);
        }
        if (trailing == null) {
            tail.setText("›");
            tail.setTextSize(26f);
        } else {
            tail.setText(trailing);
            tail.setTextSize(11f);
            if (!trailing.trim().isEmpty()) {
                accessibility.append(". ").append(trailing);
            }
        }
        setContentDescription(accessibility);
    }

    public void configureGame(String title, String detail, String glyph, int accent) {
        configureGame(title, detail, glyph, accent, null);
    }

    public void configureGame(String title, String detail, String glyph, int accent, @Nullable String trailing) {
        configure(title, detail, 0, R.color.related, R.color.primary, false, trailing);
        icon.setVisibility(GONE);
        symbol.setText(glyph);
        symbol.setTextColor(accent);
        symbol.setVisibility(VISIBLE);
        int tint = ColorUtils.blendARGB(accent, color(R.color.surface), 0.81f);
        styleBadge(tint);
        styleRow(tint);
    }

    private void resizeSquare(View view, int size) {
        var params = view.getLayoutParams();
        params.width = size;
        params.height = size;
        view.setLayoutParams(params);
    }

    private void styleBadge(int color) {
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(dp(14));
        surface.setColor(color);
        badge.setBackground(surface);
    }

    private void styleRow(int shade) {
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{shade, color(R.color.surface)});
        surface.setCornerRadius(dp(22));
        row.setBackground(surface);
    }

    private int color(int resource) { return ContextCompat.getColor(getContext(), resource); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
