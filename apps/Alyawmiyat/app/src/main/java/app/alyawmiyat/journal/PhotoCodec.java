package app.alyawmiyat.journal;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class PhotoCodec {
    static final int MAX_DIMENSION = 1600;
    private PhotoCodec() {}

    static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= 28) {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetSize(scaled(info.getSize().getWidth(), info.getSize().getHeight()),
                    scaled(info.getSize().getHeight(), info.getSize().getWidth()));
            });
        } else {
            bitmap = readLegacy(resolver, uri);
        }
        if (bitmap == null) throw new IOException("Unreadable image");
        if (bitmap.hasAlpha()) {
            Bitmap opaque = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap,0,0,null);
            bitmap.recycle();
            bitmap = opaque;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            for (int quality : new int[]{85, 74, 62, 49, 36}) {
                bytes.reset();
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes) &&
                    bytes.size() > 0 && bytes.size() <= JournalStore.MAX_PHOTO_BYTES) return bytes.toByteArray();
            }
            throw new IOException("Image is too large");
        } finally { bitmap.recycle(); }
    }

    private static int scaled(int dimension, int other) {
        int maximum = Math.max(dimension, other);
        return Math.max(1, (int) Math.round(dimension * Math.min(1.0, MAX_DIMENSION / (double) maximum)));
    }

    private static Bitmap readLegacy(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(input,null,options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Unreadable image");
        int full = Math.max(options.outWidth,options.outHeight);
        int sample = 1;
        while (full / sample > 2 * MAX_DIMENSION) sample *= 2;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream input = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input,null,options);
        }
        if (bitmap == null) throw new IOException("Unreadable image");
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input != null) orientation = new ExifInterface(input)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException ignored) {}
        Bitmap upright = rotate(bitmap, orientation);
        if (upright != bitmap) bitmap.recycle();
        Bitmap reduced = Bitmap.createScaledBitmap(upright,scaled(upright.getWidth(),upright.getHeight()),
            scaled(upright.getHeight(),upright.getWidth()),true);
        if (reduced != upright) upright.recycle();
        return reduced;
    }

    private static Bitmap rotate(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1,-1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1,1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            default: return bitmap;
        }
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
    }

    static boolean validBackupPhoto(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != (byte) 0xff || bytes[1] != (byte) 0xd8) return false;
        BitmapFactory.Options limits = new BitmapFactory.Options();
        limits.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,limits);
        return "image/jpeg".equals(limits.outMimeType) && limits.outWidth > 0 && limits.outHeight > 0 &&
            limits.outWidth <= MAX_DIMENSION && limits.outHeight <= MAX_DIMENSION;
    }
}
