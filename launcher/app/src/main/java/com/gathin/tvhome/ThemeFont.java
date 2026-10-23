package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules for a theme pack's clock font (config clock_font and clock_font_axes), with no Android types so the JVM
 * tests cover them: which file names fonts/ may hold, whether a file starts like a font the framework can read, and
 * the variation string the axes turn into. ThemePackage and ThemeStore check a pack with them on import, and
 * ThemeTypeface checks the file once more before it hands it to Typeface.Builder.
 */
final class ThemeFont {
    /** The pack folder fonts live in, one level deep like banners/ and wallpapers/. */
    static final String DIRECTORY = "fonts";
    /** At most this many variation axes: a real font has a handful (wght, wdth, slnt, ital, opsz). */
    static final int MAX_AXES = 8;
    /** Axis values are whole numbers in this range; wght runs 1 to 1000, and no registered axis goes further. */
    static final int MIN_AXIS = -10000, MAX_AXIS = 10000;
    private static final Pattern LEAF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*\\.(ttf|otf)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("[A-Za-z]{4}");
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");
    private static final BigInteger LOWEST = BigInteger.valueOf(MIN_AXIS), HIGHEST = BigInteger.valueOf(MAX_AXIS);

    private ThemeFont() {}

    /** A file name fonts/ may hold: "Fredoka.ttf", "clock-digits.OTF"; no folders, no other kind of file. */
    static boolean leaf(String name) {
        return name != null && LEAF.matcher(name).matches();
    }

    /**
     * {@code value} itself when it names a font the pack may hold ("fonts/fredoka.ttf"), or "" for anything else:
     * another folder, a nested path, a '#' suffix, a '..' anywhere (the ZIP rules refuse those names too) or a file
     * that is not .ttf or .otf. Whether the file exists is the caller's question.
     */
    static String path(String value) {
        String prefix = DIRECTORY + "/";
        if (value == null || !value.startsWith(prefix) || value.contains("..") || value.indexOf('#') >= 0) return "";
        return leaf(value.substring(prefix.length())) ? value : "";
    }

    /**
     * Whether {@code head} (the first bytes of a file, {@code length} of them read) starts with a single-font sfnt
     * tag: 00 01 00 00 (TrueType outlines), "OTTO" (CFF outlines) or "true" (old Apple TrueType). A collection
     * ("ttcf"), a WOFF or WOFF2 wrapper and anything else are refused: Typeface.Builder reads a file as one font, and
     * refusing them here keeps a misnamed file from ever reaching the framework's parser.
     */
    static boolean sfnt(byte[] head, int length) {
        if (head == null || length < 4 || head.length < 4) return false;
        int a = head[0] & 0xff, b = head[1] & 0xff, c = head[2] & 0xff, d = head[3] & 0xff;
        return a == 0 && b == 1 && c == 0 && d == 0
                || a == 'O' && b == 'T' && c == 'T' && d == 'O'
                || a == 't' && b == 'r' && c == 'u' && d == 'e';
    }

    /** {@link #sfnt(byte[], int)} for the first four bytes of {@code file}; false when it cannot be read. */
    static boolean sfnt(File file) {
        if (file == null || !file.isFile()) return false;
        byte[] head = new byte[4];
        int at = 0;
        try (InputStream in = new FileInputStream(file)) {
            for (int n; at < head.length && (n = in.read(head, at, head.length - at)) > 0;) at += n;
        } catch (IOException | SecurityException e) {
            return false;
        }
        return sfnt(head, at);
    }

    /**
     * The variation string for "tag=value" entries, as Typeface.Builder.setFontVariationSettings reads it:
     * ["wght=700", "wdth=120"] gives "'wght' 700, 'wdth' 120", in the order given. No entries (or none at all) give
     * "", the font's default instance. Null when any entry is wrong, so a pack cannot be half applied: not exactly
     * one '=', a tag that is not four ASCII letters, a tag twice, a value that is not a whole number from
     * {@link #MIN_AXIS} to {@link #MAX_AXIS} ("700.5", "+700", " 700"), or more than {@link #MAX_AXES} entries.
     */
    static String axes(List<String> entries) {
        if (entries == null || entries.isEmpty()) return "";
        if (entries.size() > MAX_AXES) return null;
        StringBuilder out = new StringBuilder();
        Set<String> tags = new HashSet<>();
        for (String entry : entries) {
            if (entry == null) return null;
            int equals = entry.indexOf('=');
            if (equals < 0 || equals != entry.lastIndexOf('=')) return null;
            String tag = entry.substring(0, equals), value = entry.substring(equals + 1);
            if (!TAG.matcher(tag).matches() || !tags.add(tag) || !INTEGER.matcher(value).matches()) return null;
            // BigInteger, so a long run of digits is out of range rather than an overflow.
            BigInteger number = new BigInteger(value);
            if (number.compareTo(LOWEST) < 0 || number.compareTo(HIGHEST) > 0) return null;
            if (out.length() > 0) out.append(", ");
            out.append('\'').append(tag).append("' ").append(number.intValue());
        }
        return out.toString();
    }
}
