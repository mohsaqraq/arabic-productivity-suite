package app.almolahazat.notes;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class PhotoDecoder {
    private static final int SIDE = 1280;

    private PhotoDecoder() {}

    static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        Bitmap bitmap = Build.VERSION.SDK_INT >= 28
            ? ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
                int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                double scale = Math.min(1d, (double) SIDE / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            }) : legacy(resolver, uri);
        if (bitmap == null) throw new IOException("Unsupported image");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int quality = 88; quality >= 28; quality -= 12) {
                output.reset();
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw new IOException("Encoding failed");
                if (output.size() <= Note.PHOTO_BYTES) return output.toByteArray();
            }
            throw new IOException("Image is too large");
        } finally { bitmap.recycle(); }
    }

    private static Bitmap legacy(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
        if (options.outHeight <= 0 || options.outWidth <= 0) throw new IOException("Invalid image dimensions");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > SIDE)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap;
        try (InputStream input = resolver.openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
        if (bitmap == null) return null;
        int orientation;
        try (InputStream input = resolver.openInputStream(uri)) {
            orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (IOException | RuntimeException ignored) { return bitmap; }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case 2: matrix.setScale(-1, 1); break;
            case 3: matrix.setRotate(180); break;
            case 4: matrix.setScale(1, -1); break;
            case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case 6: matrix.setRotate(90); break;
            case 7: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case 8: matrix.setRotate(-90); break;
            default: return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }
}
