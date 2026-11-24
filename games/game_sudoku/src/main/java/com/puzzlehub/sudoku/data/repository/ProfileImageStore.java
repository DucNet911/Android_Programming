package com.puzzlehub.sudoku.data.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class ProfileImageStore {
    private ProfileImageStore() {}

    static void importImage(Context context, Uri uri, File target, int width, int height) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = open(context, uri)) {
            BitmapFactory.decodeStream(stream, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("Không đọc được ảnh này.");
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2;
        }
        Set<Bitmap> allocated = Collections.newSetFromMap(new IdentityHashMap<>());
        File temp = null;
        try {
            Bitmap bitmap;
            try (InputStream stream = open(context, uri)) {
                bitmap = BitmapFactory.decodeStream(stream, null, options);
            }
            if (bitmap == null) {
                throw new IOException("Không đọc được ảnh này.");
            }
            allocated.add(bitmap);
            Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    orientationMatrix(context, uri), true);
            allocated.add(oriented);
            float ratio = (float) width / height;
            int cropWidth = Math.max(1, Math.min(oriented.getWidth(), (int) (oriented.getHeight() * ratio)));
            int cropHeight = Math.max(1, Math.min(oriented.getHeight(), (int) (oriented.getWidth() / ratio)));
            Bitmap crop = Bitmap.createBitmap(oriented, (oriented.getWidth() - cropWidth) / 2,
                    (oriented.getHeight() - cropHeight) / 2, cropWidth, cropHeight);
            allocated.add(crop);
            Bitmap resized = Bitmap.createScaledBitmap(crop, width, height, true);
            allocated.add(resized);
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Không tạo được thư mục ảnh.");
            }
            temp = File.createTempFile("import-", ".tmp", parent);
            try (FileOutputStream stream = new FileOutputStream(temp)) {
                if (!resized.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                    throw new IOException("Không lưu được ảnh. Hãy thử lại.");
                }
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Không lưu được ảnh. Hãy thử lại.");
            }
        } finally {
            if (temp != null) {
                temp.delete();
            }
            for (Bitmap bitmap : allocated) {
                bitmap.recycle();
            }
        }
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        InputStream stream = context.getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new IOException("Không đọc được ảnh này.");
        }
        return stream;
    }

    private static Matrix orientationMatrix(Context context, Uri uri) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            try (InputStream stream = open(context, uri)) {
                orientation = new ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, orientation);
            } catch (IOException | IllegalArgumentException ignored) {
                // Images without readable EXIF still open in their supplied orientation.
            }
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f);
            case ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f);
            case ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f);
            case ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f);
            case ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
            }
            case ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f);
                matrix.postScale(-1f, 1f);
            }
            default -> {}
        }
        return matrix;
    }
}
