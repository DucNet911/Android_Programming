package com.puzzlehub.sudoku.ui.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.puzzlehub.sudoku.game.R;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AvatarImages {
    private static final Map<String, Integer> RESOURCES = createResources();

    private AvatarImages() {}

    private static Map<String, Integer> createResources() {
        Map<String, Integer> resources = new LinkedHashMap<>();
        resources.put("cat", R.drawable.avatar_cat);
        resources.put("fox", R.drawable.avatar_fox);
        resources.put("panda", R.drawable.avatar_panda);
        resources.put("robot", R.drawable.avatar_robot);
        return Collections.unmodifiableMap(resources);
    }

    public static Map<String, Integer> resources() { return RESOURCES; }

    public static void render(ImageView view, String avatar, @Nullable File file) {
        if ("custom".equals(avatar) && file != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
                return;
            }
        }
        Integer res = RESOURCES.get(avatar);
        view.setImageResource(res != null ? res : R.drawable.avatar_cat);
    }
}
