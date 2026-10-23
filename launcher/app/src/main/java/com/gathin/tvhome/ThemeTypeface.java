package com.gathin.tvhome;

import android.graphics.Typeface;

import java.io.File;

/**
 * Builds the Home clock's typeface from a theme pack's font (config clock_font, see ThemeFont), so a theme can bring
 * its own digits without the APK carrying the font. Home recreates on every theme or settings change, and a variable
 * font takes a moment to parse on a TV's CPU, so the last typeface is kept for the life of the process, keyed by the
 * file's real path, length and time and the axes: the same font file gives the same object back.
 */
final class ThemeTypeface {
    private static final Object LOCK = new Object();
    /** The one typeface kept, and what it was built from; a font that failed is kept as null, so it isn't parsed again. */
    private static String cachedKey;
    private static Typeface cached;

    private ThemeTypeface() {}

    /**
     * The typeface for {@code path} ("fonts/fredoka.ttf", from Config.clockFont) in {@code themeDirectory}, at the
     * variation {@code axes} ("'wght' 700, 'wdth' 120", or "" for the default instance), or null for no font: no path,
     * a file outside the pack's fonts/ folder, too large, not starting like a font, or one the framework cannot read.
     * The pack was checked at import; this checks again before the file reaches the framework's parser, since the
     * snapshot is read long after.
     */
    static Typeface load(File themeDirectory, String path, String axes) {
        if (themeDirectory == null || ThemeFont.path(path).isEmpty()) return null;
        File font = ThemePackage.inside(themeDirectory, ThemeFont.DIRECTORY, path);
        if (font == null) return null;
        long length = font.length();
        if (length < 4 || length > ThemePackage.MAX_FONT) return null;
        String variation = axes == null ? "" : axes;
        String key = font.getPath() + '\n' + length + '\n' + font.lastModified() + '\n' + variation;
        synchronized (LOCK) {
            if (key.equals(cachedKey)) return cached;
        }
        Typeface typeface = null;
        if (ThemeFont.sfnt(font)) {
            try {
                Typeface.Builder builder = new Typeface.Builder(font);
                if (!variation.isEmpty()) builder.setFontVariationSettings(variation);
                // Null when the framework cannot read the file (no fallback family is set).
                typeface = builder.build();
            } catch (RuntimeException e) {
                typeface = null;
            }
        }
        synchronized (LOCK) {
            cachedKey = key;
            cached = typeface;
        }
        return typeface;
    }
}
