package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.IOException;

/** Bounded external-theme artwork loader and a 320 by 180 card drawable for the existing banner ImageView. */
final class ThemeArtwork {
    private static final int CARD_W = 320, CARD_H = 180;
    private static final int ANIMAL_WOOD = 0xFF735332, ANIMAL_GRAIN = 0x338F7047, ANIMAL_PINE = 0xFF295E3B;
    private static final int LEGACY_SLATE = 0xFF12272B, LEGACY_JADE = 0xFF1D756A, LEGACY_GOLD = 0xFFD8B86A;
    private static final int ZELDA_TOP = 0xFF4B5C48, ZELDA_BOTTOM = 0xFF304238;
    private static final int ZELDA_EDGE = 0x66D8C18A;

    private ThemeArtwork() {}

    /**
     * Loads a validated theme banner once while entries are built. Invalid paths, files, dimensions and decoder
     * failures intentionally look like absent artwork. A #N suffix treats the source as a 4x4 atlas and renders its
     * selected cell over the theme's quiet card ground rather than cropping its sprite.
     */
    static Drawable load(Context context, File directory, String reference, String headerStyle) {
        ThemeArtReference.Value value = ThemeArtReference.parse(reference);
        if (value == null || directory == null || !directory.isDirectory()) return null;
        File file = new File(directory, value.path);
        if (!safeFile(directory, file) || file.length() <= 0 || file.length() > ThemeArtReference.MAX_FILE_BYTES) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (!ThemeArtReference.safeBounds(bounds.outWidth, bounds.outHeight) || !value.fits(bounds.outWidth, bounds.outHeight)) return null;
            Bitmap bitmap;
            if (value.cell >= 0 || value.hasRegion()) {
                int left = value.hasRegion() ? value.x : ThemeArtReference.cellLeft(bounds.outWidth, value.cell);
                int top = value.hasRegion() ? value.y : ThemeArtReference.cellTop(bounds.outHeight, value.cell);
                int right = value.hasRegion() ? value.x + value.width : ThemeArtReference.cellRight(bounds.outWidth, value.cell);
                int bottom = value.hasRegion() ? value.y + value.height : ThemeArtReference.cellBottom(bounds.outHeight, value.cell);
                int longest = Math.max(right - left, bottom - top);
                BitmapFactory.Options options = sampled(longest, 512);
                BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(file.getPath(), false);
                try { bitmap = decoder.decodeRegion(new Rect(left, top, right, bottom), options); }
                finally { decoder.recycle(); }
            } else {
                BitmapFactory.Options options = sampled(Math.max(bounds.outWidth, bounds.outHeight), 768);
                bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            }
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return null;
            boolean animal = HeaderStyle.ANIMAL_CROSSING.equals(headerStyle);
            boolean zelda = HeaderStyle.ZELDA.equals(headerStyle);
            boolean fit = value.cell >= 0 || value.hasRegion() || bitmap.hasAlpha();
            return new CardArtwork(bitmap, fit, animal, zelda);
        } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static BitmapFactory.Options sampled(int longest, int cap) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = ThemeArtReference.sampleFor(longest, cap);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return options;
    }

    private static boolean safeFile(File directory, File file) {
        try {
            File banners = new File(directory, "banners");
            return file.isFile() && file.getCanonicalPath().startsWith(banners.getCanonicalPath() + File.separator);
        } catch (IOException ignored) { return false; }
    }

    /** A static themed ground means transparent sprites never inherit black bars or an app icon. */
    private static final class CardArtwork extends Drawable {
        private final Bitmap bitmap;
        private final boolean fit, animal, zelda;
        private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grain = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint art = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect source;
        private final RectF destination = new RectF();
        private final RectF innerEdge = new RectF();
        private int alpha = 255;
        private LinearGradient zeldaGround;

        CardArtwork(Bitmap bitmap, boolean fit, boolean animal, boolean zelda) {
            this.bitmap = bitmap; this.fit = fit; this.animal = animal; this.zelda = zelda;
            source = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        @Override protected void onBoundsChange(Rect bounds) {
            if (zelda && bounds.width() > 0 && bounds.height() > 0) {
                zeldaGround = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                        ZELDA_TOP, ZELDA_BOTTOM, Shader.TileMode.CLAMP);
            } else {
                zeldaGround = null;
            }
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            drawGround(canvas, b);
            float width = b.width(), height = b.height();
            if (width <= 0 || height <= 0) return;
            float scale = fit ? Math.min(width * .9f / source.width(), height * .9f / source.height())
                    : Math.max(width / source.width(), height / source.height());
            float w = source.width() * scale, h = source.height() * scale;
            destination.set(b.centerX() - w / 2, b.centerY() - h / 2, b.centerX() + w / 2, b.centerY() + h / 2);
            canvas.drawBitmap(bitmap, source, destination, art);
        }

        private void drawGround(Canvas canvas, Rect b) {
            ground.setStyle(Paint.Style.FILL);
            ground.setShader(null);
            ground.setColor(animal ? ANIMAL_WOOD : LEGACY_SLATE);
            setAlpha(ground, animal ? ANIMAL_WOOD : LEGACY_SLATE);
            if (zelda) {
                if (zeldaGround == null) onBoundsChange(b);
                ground.setShader(zeldaGround);
            }
            canvas.drawRect(b, ground);
            if (animal) {
                grain.setColor(ANIMAL_GRAIN); setAlpha(grain, ANIMAL_GRAIN); grain.setStrokeWidth(Math.max(1, b.height() / 22f));
                for (int i = 1; i <= 4; i++) {
                    float y = b.top + b.height() * i / 5f;
                    canvas.drawLine(b.left, y, b.right, y - b.height() * .04f, grain);
                }
                grain.setColor(ANIMAL_PINE); setAlpha(grain, ANIMAL_PINE); grain.setStyle(Paint.Style.FILL);
                canvas.drawCircle(b.right - b.width() * .08f, b.top + b.height() * .18f, Math.max(2, b.height() * .035f), grain);
            } else if (zelda) {
                float inset = Math.max(2, b.height() * .025f);
                innerEdge.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
                grain.setStyle(Paint.Style.STROKE);
                grain.setStrokeWidth(Math.max(1, b.height() / 90f));
                grain.setColor(ZELDA_EDGE);
                setAlpha(grain, ZELDA_EDGE);
                canvas.drawRoundRect(innerEdge, b.height() * .07f, b.height() * .07f, grain);
            } else {
                grain.setStyle(Paint.Style.STROKE); grain.setStrokeWidth(Math.max(1, b.height() / 45f)); grain.setColor(LEGACY_JADE); setAlpha(grain, LEGACY_JADE);
                canvas.drawRect(b.left + 2, b.top + 2, b.right - 2, b.bottom - 2, grain);
                grain.setColor(LEGACY_GOLD); setAlpha(grain, LEGACY_GOLD);
                float x = b.left + b.width() * .07f, y = b.top + b.height() * .16f, s = b.height() * .065f;
                canvas.drawLine(x, y, x + s, y, grain); canvas.drawLine(x, y, x, y + s, grain);
                x = b.right - b.width() * .07f; y = b.bottom - b.height() * .16f;
                canvas.drawLine(x, y, x - s, y, grain); canvas.drawLine(x, y, x, y - s, grain);
            }
        }

        private void setAlpha(Paint paint, int sourceColor) { paint.setAlpha(((sourceColor >>> 24) & 0xff) * alpha / 255); }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; ground.setAlpha(alpha); grain.setAlpha(alpha); art.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { ground.setColorFilter(filter); grain.setColorFilter(filter); art.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return CARD_W; }
        @Override public int getIntrinsicHeight() { return CARD_H; }
    }
}
