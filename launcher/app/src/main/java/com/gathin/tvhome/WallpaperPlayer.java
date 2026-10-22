package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays and rotates a theme's local wallpaper images while Home is visible. A rotated picture fades in over the one
 * before it (WallpaperFade) on a second view stacked right above, under the dim shade; when it is fully in, it moves
 * down to the first view and the second one empties again, so only one picture stays decoded between fades.
 */
final class WallpaperPlayer {
    /** Told on the main thread when a rotation puts another picture on screen. */
    interface OnChange {
        /**
         * {@code fadeMs}: the new picture has just started fading in and is fully in that many ms from now, so what
         * follows it can keep time (WallpaperFade.halfway); 0 when it was cut in.
         */
        void onWallpaperChanged(long fadeMs);
    }

    private final FrameLayout root;
    /** The picture on screen; during a fade, the one the next picture fades in over. */
    private final ImageView image;
    /** The next picture fades in here, invisible between fades; null when no fade can happen (see the constructor). */
    private final ImageView incoming;
    /** WallpaperFade.duration: 0 with animations off or reduced_motion on, and the pictures are cut in. */
    private final long fadeMs;
    /** The incoming view's alpha, 0 to 1, made once and started again for each fade. */
    private ObjectAnimator fader;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** Runs on the main thread when a rotation puts another picture on screen (the Odyssey header, the dock's tint). */
    private OnChange onChange;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        fadeMs = WallpaperFade.duration(config.animations, config.reducedMotion);
        image = picture(context);
        boolean shown = config.wallpaperEnabled && !files.isEmpty();
        // Only a collection that rotates gets the second view. It is added now, while Home is laid out for the first
        // time, rather than at the first fade, and stays invisible (never drawn) between fades. An on-TV pick is a
        // collection of one, and recreates Home with its picture already shown, so it never fades.
        incoming = shown && fadeMs > 0 && interval > 0 && files.size() > 1 ? picture(context) : null;
        if (shown) {
            int index = 0;
            root.addView(image, index++, fill());
            if (incoming != null) {
                incoming.setVisibility(View.INVISIBLE);
                root.addView(incoming, index++, fill());
                fader = ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, 1f);
                fader.setInterpolator(WallpaperFade::eased);
                fader.setDuration(fadeMs);
                fader.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        settle();   // its natural end, end() or cancel()
                    }
                });
            }
            // No shade layer at all when the theme doesn't dim: a full-screen view is overdraw on 1 GB TVs.
            if (config.wallpaperDim > 0) {
                View shade = new View(context);
                shade.setBackgroundColor((config.wallpaperDim * 255 / 100) << 24);
                shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(shade, index, fill());
            }
            showFirst();
        }
    }

    private static ImageView picture(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background. It is cut in, never faded.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                displayed = first.getName();
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = WallpaperRotation.pick(files, previous, order, random);
        }
    }

    void start() {
        active = true;
        update();
    }

    /** Home is covered or going: a fade in progress lands at once, the new picture fully in. */
    void stop() {
        active = false;
        land();
    }

    void close() {
        active = false;
        closed = true;
        land();
        decoder.shutdownNow();
    }

    /** The filename of the picture on screen ("01-cap.jpg"), or "" when none is shown. */
    String displayed() {
        return displayed;
    }

    /**
     * {@code listener} runs as each later change of picture starts, with how long its fade takes; the one Home opens
     * with is already {@link #displayed()}.
     */
    void setOnChange(OnChange listener) {
        onChange = listener;
    }

    void update() {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        File selected = WallpaperRotation.find(files, previous);
        long changed = preferences.getLong("changed", 0);
        // A change counts from this check, not from when its picture is decoded and shown a few hundred ms later: the
        // next minute tick comes a minute after this check, so a one-minute rotation is due on every tick rather than
        // falling those few hundred ms short and moving on every other one.
        long now = System.currentTimeMillis();
        if (selected == null || WallpaperRotation.due(changed, now, interval)) {
            selected = WallpaperRotation.pick(files, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        land();   // a fade still running lets go of the picture under it before another one is decoded
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            // Starts the texture upload on the RenderThread now, so the fade's first frame doesn't wait for it.
            if (bitmap != null) bitmap.prepareToDraw();
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                long fade = show(bitmap);
                displayed = next.getName();
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed).putLong("changed", now).apply();
                }
                if (onChange != null) onChange.onWallpaperChanged(fade);
            });
        });
    }

    /**
     * Puts a rotated picture on screen: faded in over the one there, or cut in when nothing can fade or nothing is
     * shown yet. Returns the fade's length, 0 for a cut.
     */
    private long show(Bitmap bitmap) {
        land();
        // areAnimatorsEnabled: the TV's animator duration scale is 0, so a fade would end the moment it starts anyway.
        if (incoming == null || image.getDrawable() == null || !ValueAnimator.areAnimatorsEnabled()) {
            image.setImageBitmap(bitmap);
            return 0;
        }
        incoming.setAlpha(0f);
        incoming.setImageBitmap(bitmap);
        incoming.setVisibility(View.VISIBLE);
        fader.start();
        return fadeMs;
    }

    /** Ends a fade in progress at once (its listener then settles it); nothing when none runs. */
    private void land() {
        if (fader != null && fader.isStarted()) fader.end();
    }

    /**
     * After a fade, run to its end or cut short: the new picture moves down to {@link #image}, letting go of the old
     * one, and {@link #incoming} empties and hides again. Both in one main-thread step, so no frame shows a gap.
     */
    private void settle() {
        // The Bitmap moves, not the Drawable. setImageBitmap fills a BitmapDrawable the ImageView keeps for reuse and
        // empties (setBitmap(null)) once the view is handed any other drawable, null included. Handing that drawable
        // itself to image would keep its picture only because getDrawable() happens to make incoming give it up first;
        // instead image takes the picture into a drawable of its own, dropping the old one, before incoming lets go.
        Drawable drawable = incoming.getDrawable();
        Bitmap picture = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
        if (picture != null) image.setImageBitmap(picture);
        incoming.setImageDrawable(null);
        incoming.setVisibility(View.INVISIBLE);
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
