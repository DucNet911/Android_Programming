package com.puzzlehub.sudoku.ui.social;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class RankArt {
    private static final Map<CompetitiveRank, Bitmap> PICTURES = new EnumMap<>(CompetitiveRank.class);
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private RankArt() {}

    public static String points(long value) {
        return NumberFormat.getIntegerInstance(VIETNAMESE).format(value);
    }

    public static void show(ImageView view, CompetitiveRank rank) {
        if (PICTURES.isEmpty()) {
            load(view.getResources());
        }
        view.setImageBitmap(PICTURES.get(rank));
    }

    private static void load(Resources resources) {
        Bitmap atlas = BitmapFactory.decodeResource(resources, R.drawable.rank_badges);
        PICTURES.put(CompetitiveRank.WARRIOR, Bitmap.createBitmap(atlas, 0, 0, 512, 492));
        PICTURES.put(CompetitiveRank.ELITE, Bitmap.createBitmap(atlas, 512, 0, 512, 489));
        PICTURES.put(CompetitiveRank.EXPERT, Bitmap.createBitmap(atlas, 1024, 0, 512, 491));
        PICTURES.put(CompetitiveRank.GRANDMASTER, Bitmap.createBitmap(atlas, 0, 512, 512, 512));
        PICTURES.put(CompetitiveRank.EPIC, Bitmap.createBitmap(atlas, 512, 491, 512, 512));
        PICTURES.put(CompetitiveRank.LEGEND, Bitmap.createBitmap(atlas, 1024, 498, 512, 512));
    }
}
