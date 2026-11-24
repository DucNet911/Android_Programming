package com.puzzlehub.sudoku.ui.profile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.data.model.ProfileCover;
import com.puzzlehub.sudoku.data.model.ProfileCovers;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.BitmapEffects;
import androidx.core.util.Consumer;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ProfileAppearance {
    private static final Map<Integer, Bitmap> SOFTENED = new HashMap<>();

    private ProfileAppearance() {}

    private static Bitmap softImage(Context context, ProfileCover cover) {
        Bitmap cached = SOFTENED.get(cover.image());
        if (cached != null) {
            return cached;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        options.inMutable = true;
        Bitmap bitmap = Objects.requireNonNull(BitmapFactory.decodeResource(context.getResources(), cover.image(), options));
        BitmapEffects.blur(bitmap, 2, 2);
        SOFTENED.put(cover.image(), bitmap);
        return bitmap;
    }

    static void render(View surface, ImageView image, View scrim, PlayerProfile profile, @Nullable File file) {
        ProfileCover cover = ProfileCovers.find(profile.background());
        if (cover == null) {
            cover = ProfileCovers.presets().get(0);
        }
        surface.setBackgroundColor(0xFF132447);
        surface.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        28 * view.getResources().getDisplayMetrics().density);
            }
        });
        surface.setClipToOutline(true);
        Bitmap photo = null;
        if ("custom".equals(profile.background()) && file != null) {
            photo = BitmapFactory.decodeFile(file.getPath());
        }
        if (photo == null) {
            photo = softImage(surface.getContext(), cover);
        }
        image.setImageBitmap(photo);
        image.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xA8051027, 0x35051027}));
    }

    static void picker(Fragment fragment, String selected, boolean hasPhoto, Consumer<String> onChoose, Runnable onUpload) {
        Context context = fragment.requireContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        Dialog[] dialog = new Dialog[1];
        Consumer<String> choose = value -> {
            onChoose.accept(value);
            dialog[0].dismiss();
        };
        List<ProfileCover> presets = ProfileCovers.presets();
        LinearLayout strip = null;
        for (int index = 0; index < presets.size(); index++) {
            if (index % 2 == 0) {
                strip = new LinearLayout(context);
                strip.setOrientation(LinearLayout.HORIZONTAL);
                content.addView(strip);
            }
            ProfileCover cover = presets.get(index);
            boolean chosen = ProfileCovers.canonical(selected).equals(cover.id());
            strip.addView(tile(context, cover, chosen, () -> choose.accept(cover.id())));
        }
        if (hasPhoto) {
            MaterialButton custom = new MaterialButton(context);
            custom.setText("Dùng ảnh đã tải");
            custom.setTextSize(12);
            custom.setOnClickListener(view -> choose.accept("custom"));
            content.addView(custom);
        }
        dialog[0] = AppDialogs.form(fragment, "Đổi nền hồ sơ", content, "Tải ảnh", "Đóng", () -> {
            onUpload.run();
            return true;
        });
    }

    private static MaterialCardView tile(Context context, ProfileCover cover, boolean chosen, Runnable onClick) {
        MaterialCardView tile = new MaterialCardView(context);
        tile.setRadius(dp(context, 16));
        tile.setCardElevation(0);
        tile.setStateListAnimator(null);
        tile.setStrokeWidth(0);
        String label = cover.label();
        if (chosen) {
            tile.setStrokeWidth(dp(context, 2));
            label = "✓ " + label;
        }
        tile.setStrokeColor(ContextCompat.getColor(context, R.color.primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 90), 1f);
        int edge = dp(context, 4);
        params.setMargins(edge, edge, edge, edge);
        tile.setLayoutParams(params);
        tile.setContentDescription("Nền " + cover.label());
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setSelected(chosen);
        tile.setOnClickListener(view -> onClick.run());
        FrameLayout frame = new FrameLayout(context);
        ImageView art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageBitmap(softImage(context, cover));
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(art, new FrameLayout.LayoutParams(-1, -1));

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(12);
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.BOTTOM | Gravity.START);
        name.setPadding(dp(context, 10), 0, dp(context, 8), dp(context, 10));
        name.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, 0xB8051027}));
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(name, new FrameLayout.LayoutParams(-1, -1));
        tile.addView(frame);
        return tile;
    }

    private static int dp(Context context, int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value);
    }
}
