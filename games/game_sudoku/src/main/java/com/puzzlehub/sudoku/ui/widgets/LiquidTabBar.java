package com.puzzlehub.sudoku.ui.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.utils.SudokuCallbacks.IntAction;
import java.util.Arrays;
import java.util.List;

public final class LiquidTabBar extends LinearLayout {
    private record Item(int id, int icon, String label) {}

    private static final List<Item> ITEMS = Arrays.asList(
            new Item(R.id.nav_home, R.drawable.ic_home, "Trang chủ"),
            new Item(R.id.nav_explore, R.drawable.ic_explore, "Khám phá"),
            new Item(R.id.nav_leaderboard, R.drawable.ic_stats, "Bảng xếp hạng"),
            new Item(R.id.nav_settings, R.drawable.ic_profile, "Cá nhân"));
    private IntAction onTabSelected;

    public LiquidTabBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        for (Item item : ITEMS) {
            addView(createTab(context, item));
        }
        select(R.id.nav_home);
    }

    public void setOnTabSelected(IntAction selection) {
        onTabSelected = selection;
    }

    private LinearLayout createTab(Context context, Item item) {
        LinearLayout tab = new LinearLayout(context);
        tab.setId(item.id());
        tab.setOrientation(VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setContentDescription(item.label());
        tab.setOnClickListener(button -> {
            if (onTabSelected != null) {
                onTabSelected.accept(item.id());
            }
        });

        ImageView icon = new ImageView(context);
        icon.setImageResource(item.icon());
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setLayoutParams(new LayoutParams(dp(22), dp(22)));
        tab.addView(icon);

        TextView label = new TextView(context);
        label.setText(item.label());
        label.setTextSize(10);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 8, 10, 1, TypedValue.COMPLEX_UNIT_SP);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(15));
        labelParams.topMargin = dp(5);
        label.setLayoutParams(labelParams);
        tab.addView(label);
        return tab;
    }

    public void select(int selectedId) {
        for (int index = 0; index < ITEMS.size(); index++) {
            LinearLayout tab = (LinearLayout) getChildAt(index);
            boolean active = ITEMS.get(index).id() == selectedId;
            tab.setSelected(active);
            int colorId = R.color.muted;
            if (active) {
                colorId = R.color.primary;
            }
            int color = ContextCompat.getColor(getContext(), colorId);
            ((ImageView) tab.getChildAt(0)).setColorFilter(color);
            ((TextView) tab.getChildAt(1)).setTextColor(color);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
