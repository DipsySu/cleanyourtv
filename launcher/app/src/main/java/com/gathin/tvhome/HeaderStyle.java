package com.gathin.tvhome;

import java.util.Map;

/**
 * Which header Home draws (config header_style), the kingdom line the Odyssey header puts before the greeting, and the
 * coin it puts before the clock: an icon from the theme pack, or the gold vector coin. Pure logic with no Android
 * types, so the JVM tests cover it; OdysseyHeader does the styling and decodes the icon.
 */
final class HeaderStyle {
    /** The thin greeting, clock and date and the frosted pills Home always had. */
    static final String DEFAULT = "default";
    /** The "game HUD" header for the Mario Odyssey theme pack (OdysseyHeader). */
    static final String ODYSSEY = "odyssey";
    /** A warm island field guide header, with leaf-green controls. */
    static final String ANIMAL_CROSSING = "animal_crossing";
    /** A quiet Hyrule field header, with jade, slate and antique-gold controls. */
    static final String ZELDA = "zelda";
    /** Between the kingdom and the greeting: "帽子之国 · 下午好". */
    static final String SEPARATOR = " · ";
    /**
     * wallpaper_coins names an image in the theme pack's icons/ folder ("icons/coin-cap.webp"); every other picture
     * shows the gold vector coin, ic_odyssey_coin, the one coin the APK itself has.
     */
    static final String ICONS = "icons/";

    private HeaderStyle() {}

    /** A known style, or {@link #DEFAULT} for anything else: missing, misspelt or from a newer launcher. */
    static String parse(String value) {
        if (value == null) return DEFAULT;
        String style = value.trim();
        if (ODYSSEY.equals(style) || ANIMAL_CROSSING.equals(style) || ZELDA.equals(style)) return style;
        return DEFAULT;
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
     * The icon wallpaper_coins gives the picture on screen ("icons/coin-cap.webp"), looked up as {@link #kingdom}
     * does, or "" for the vector coin: no picture, no entry, or a value that is not {@link #coinPath a coin path}.
     * The resource names older configs used (coin_odyssey_cap, ic_odyssey_coin) are not paths, so they show the
     * vector coin too. Whether the file is there and decodes is left to the caller (OdysseyHeader.coin), which falls
     * back to the vector coin as well.
     */
    static String coin(Map<String, String> coins, String wallpaper) {
        String path = kingdom(coins, wallpaper);
        return coinPath(path) ? path : "";
    }

    /**
     * Whether {@code value} is a path a coin may have: exactly "icons/" and an image name the pack's ZIP rules accept
     * (ThemePackage.image: .jpg, .jpeg, .png or .webp, in any case), with no '..', no '#' suffix and no folder below
     * icons/. ThemeStore checks wallpaper_coins in a pack with the same rule, so a pack cannot name a coin that this
     * would then drop.
     */
    static boolean coinPath(String value) {
        if (value == null || !value.startsWith(ICONS) || value.contains("..") || value.indexOf('#') >= 0) return false;
        return ThemePackage.image(value.substring(ICONS.length()));
    }
}
