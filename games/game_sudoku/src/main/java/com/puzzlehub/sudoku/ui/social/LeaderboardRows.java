package com.puzzlehub.sudoku.ui.social;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.data.repository.RankingEntry;
import com.puzzlehub.sudoku.utils.Format;

final class LeaderboardRows {
    private record Palette(int lightTop, int lightBottom, int darkTop, int darkBottom,
                           int lightAccent, int darkAccent, int rim) {}

    private static final Palette GOLD = new Palette(0xFF753706, 0xFFA35C18, 0xFF622A0E, 0xFF8C4C16,
            0xFFFFE9AA, 0xFFFFE4A4, 0xFFF4BB59);
    private static final Palette SILVER = new Palette(0xFF254263, 0xFF416991, 0xFF233C5A, 0xFF32577A,
            0xFFDAEEFF, 0xFFDAEEFF, 0xFF9EBEDB);
    private static final Palette BRONZE = new Palette(0xFF6C2A20, 0xFF96432F, 0xFF602B26, 0xFF843C2C,
            0xFFFFDDC8, 0xFFFFDDC8, 0xFFE09673);
    private static final Palette STANDARD = new Palette(0xFFF2F6FE, 0xFFE7F1F7, 0xFF203449, 0xFF263D4D,
            0xFF305B78, 0xFFB9DFF6, 0xFF9CB9D2);
    private static final Palette OWN = new Palette(0xFFEAF9F5, 0xFFD0EDE6, 0xFF1E423D, 0xFF255349,
            0xFF205E53, 0xFFB6EDE1, 0xFF69AEA2);
    private static final Palette[] PODIUM = {GOLD, SILVER, BRONZE};
    private static final int[] PODIUM_BADGES = {
            R.drawable.ic_podium_gold, R.drawable.ic_podium_silver, R.drawable.ic_podium_bronze
    };

    private LeaderboardRows() {}

    static View create(Context context, int position, RankingEntry entry, boolean own) {
        boolean topThree = position <= PODIUM.length;
        Palette palette = STANDARD;
        if (topThree) {
            palette = PODIUM[position - 1];
        } else if (own) {
            palette = OWN;
        }
        boolean night = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int accent = palette.lightAccent();
        int top = palette.lightTop();
        int bottom = palette.lightBottom();
        if (night) {
            accent = palette.darkAccent();
            top = palette.darkTop();
            bottom = palette.darkBottom();
        }
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(context, 8);
        card.setLayoutParams(rowParams);
        int minimumHeight = 58;
        if (topThree) {
            minimumHeight = 77;
        }
        card.setMinimumHeight(dp(context, minimumHeight));
        GradientDrawable cardShape = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{top, bottom});
        cardShape.setCornerRadius(dp(context, 19));
        cardShape.setStroke(dp(context, 1), palette.rim());
        card.setBackground(cardShape);

        LinearLayout rankMark = new LinearLayout(context);
        rankMark.setOrientation(LinearLayout.VERTICAL);
        rankMark.setGravity(Gravity.CENTER);
        card.addView(rankMark, new LinearLayout.LayoutParams(dp(context, 51), -2));
        if (topThree) {
            ImageView badge = new ImageView(context);
            badge.setImageResource(PODIUM_BADGES[position - 1]);
            badge.setContentDescription("Vương miện hạng " + position);
            badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
            rankMark.addView(badge, new LinearLayout.LayoutParams(dp(context, 49), dp(context, 47)));
            TextView place = label(context, "HẠNG " + position, 9, accent, true);
            place.setLetterSpacing(0.07f);
            place.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            rankMark.addView(place);
        } else {
            rankMark.addView(label(context, String.valueOf(position), 15, accent, true));
        }

        LinearLayout names = new LinearLayout(context);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.setMarginStart(dp(context, 9));
        nameParams.setMarginEnd(dp(context, 8));
        String name = entry.getPlayer().getName();
        if (own) {
            name += " · Bạn";
        }
        int ink = ContextCompat.getColor(context, R.color.ink);
        if (topThree) {
            ink = 0xFFFFFBF4;
        }
        names.addView(label(context, name, 15, ink, true));

        LinearLayout region = new LinearLayout(context);
        region.setGravity(Gravity.CENTER_VERTICAL);
        region.setOrientation(LinearLayout.HORIZONTAL);
        ImageView flag = new ImageView(context);
        flag.setImageResource(CountryPicker.flag(entry.getPlayer().getCountry()));
        flag.setScaleType(ImageView.ScaleType.FIT_CENTER);
        flag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        region.addView(flag, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 13)));
        TextView subtitle = label(context, CountryPicker.name(entry.getPlayer().getCountry()), 11, accent, false);
        subtitle.setPaddingRelative(dp(context, 5), 0, 0, 0);
        region.addView(subtitle);
        names.addView(region);
        card.addView(names, nameParams);

        TextView time = label(context, Format.duration(entry.getDurationMs() / 1000L), 17, accent, true);
        time.setGravity(Gravity.CENTER);
        time.setFontFeatureSettings("tnum");
        time.setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(dp(context, 12));
        int chipColor = 0x90FFFFFF;
        if (topThree) {
            chipColor = 0x480D1425;
            chip.setStroke(dp(context, 1), 0x45FFFFFF);
        } else if (night) {
            chipColor = 0x450D2034;
        }
        chip.setColor(chipColor);
        time.setBackground(chip);
        card.addView(time, new LinearLayout.LayoutParams(-2, -2));
        return card;
    }

    private static TextView label(Context context, String value, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
