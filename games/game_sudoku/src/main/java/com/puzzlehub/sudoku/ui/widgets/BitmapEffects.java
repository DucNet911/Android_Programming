package com.puzzlehub.sudoku.ui.widgets;

import android.graphics.Bitmap;

/** Separable blur for the glass backdrop and profile cover previews. */
public final class BitmapEffects {
    private BitmapEffects() {}

    public static void blur(Bitmap bitmap, int radius, int passes) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        int[] scratch = new int[pixels.length];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < passes; pass++) {
            boxBlur(pixels, scratch, width, height, true, radius);
            boxBlur(scratch, pixels, width, height, false, radius);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static void boxBlur(int[] input, int[] output, int width, int height, boolean horizontal, int radius) {
        int count = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = 0;
                int red = 0;
                int green = 0;
                int blue = 0;
                for (int step = -radius; step <= radius; step++) {
                    int sampleX = x;
                    int sampleY = y;
                    if (horizontal) {
                        sampleX = Math.max(0, Math.min(x + step, width - 1));
                    } else {
                        sampleY = Math.max(0, Math.min(y + step, height - 1));
                    }
                    int pixel = input[sampleY * width + sampleX];
                    alpha += pixel >>> 24;
                    red += (pixel >>> 16) & 255;
                    green += (pixel >>> 8) & 255;
                    blue += pixel & 255;
                }
                output[y * width + x] = (alpha / count << 24) | (red / count << 16)
                        | (green / count << 8) | blue / count;
            }
        }
    }
}
