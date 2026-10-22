package com.gathin.tvhome;

import java.util.Map;

/**
 * Which header Home draws (config header_style), the kingdom line the Odyssey header puts before the greeting, and the
 * coin it puts before the clock. Pure logic with no Android types, so the JVM tests cover it; OdysseyHeader does the
 * styling.
 */
final class HeaderStyle {
    /** The thin greeting, clock and date and the frosted pills Home always had. */
    static final String DEFAULT = "default";
    /** The "game HUD" header for the Mario Odyssey preset (OdysseyHeader). */
    static final String ODYSSEY = "odyssey";
    /** Between the kingdom and the greeting: "帽子之国 · 下午好". */
    static final String SEPARATOR = " · ";
    /** wallpaper_coins names a bundled coin_* drawable (the kingdoms' WebPs in drawable-nodpi)... */
    static final String COIN_PREFIX = "coin_";
    /** ...or the vector coin, the one shown for every picture without a coin of its own. */
    static final String VECTOR_COIN = "ic_odyssey_coin";

    private HeaderStyle() {}

    /** A known style, or {@link #DEFAULT} for anything else: missing, misspelt or from a newer launcher. */
    static String parse(String value) {
        return value != null && ODYSSEY.equals(value.trim()) ? ODYSSEY : DEFAULT;
    }

    /**
     * The name wallpaper_names gives the picture on screen, by its filename ("01-cap.jpg"), or "" when it has none
     * or no picture is shown (a plain colour, or the wallpaper switched off on the TV).
     */
    static String kingdom(Map<String, String> names, String wallpaper) {
        if (wallpaper == null || wallpaper.isEmpty()) return "";
        String name = names.get(wallpaper);
        return name == null ? "" : name;
    }

    /** "帽子之国 · 下午好": the kingdom, then the greeting; the greeting alone when the kingdom is unknown. */
    static String greeting(String kingdom, String greeting) {
        return kingdom == null || kingdom.isEmpty() ? greeting : kingdom + SEPARATOR + greeting;
    }

    /**
     * The drawable wallpaper_coins gives the picture on screen ("coin_odyssey_cap"), looked up as {@link #kingdom}
     * does, or "" for the vector coin: no picture, no entry, {@link #VECTOR_COIN} itself, or a value that is not a
     * coin_* resource name (lower case letters, digits and underscores, as aapt names drawables). Whether the APK has
     * that drawable is left to the caller; one it doesn't have shows the vector coin too.
     */
    static String coin(Map<String, String> coins, String wallpaper) {
        String name = kingdom(coins, wallpaper);
        if (name.length() <= COIN_PREFIX.length() || !name.startsWith(COIN_PREFIX)) return "";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) return "";
        }
        return name;
    }
}
