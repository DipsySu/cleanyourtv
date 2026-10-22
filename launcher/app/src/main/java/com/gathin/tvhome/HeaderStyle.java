package com.gathin.tvhome;

import java.util.Map;

/**
 * Which header Home draws (config header_style), and the kingdom line the Odyssey header puts before the greeting.
 * Pure logic with no Android types, so the JVM tests cover it; OdysseyHeader does the styling.
 */
final class HeaderStyle {
    /** The thin greeting, clock and date and the frosted pills Home always had. */
    static final String DEFAULT = "default";
    /** The "game HUD" header for the Mario Odyssey preset (OdysseyHeader). */
    static final String ODYSSEY = "odyssey";
    /** Between the kingdom and the greeting: "帽子之国 · 下午好". */
    static final String SEPARATOR = " · ";

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
}
