package com.gathin.tvhome;

import java.util.Map;

/**
 * The dock tray's colours for the picture on screen (config wallpaper_tints, "filename=#RRGGBB"): a soft translucent
 * tint in the wallpaper's own hue instead of the near-opaque dark tray. Pure colour arithmetic with no Android types,
 * so the JVM tests hold it to the table the Odyssey kingdoms' tints were chosen with; DockDecoration draws it.
 *
 * From the base colour B: the band runs from B at 42% down to B darkened to 45% at 56%, and the 1dp edge is B mixed
 * 72% of the way to white, from 30% down to 16%. Channels round as Math.round does, in sRGB.
 */
final class DockTint {
    /** No tint: the tray keeps its dark grey-blue look. No parsed colour is 0, as they are all opaque. */
    static final int NONE = 0;
    private static final int BAND_TOP_ALPHA = 0x6B, BAND_BOTTOM_ALPHA = 0x8F;   // 0.42, 0.56
    private static final int EDGE_TOP_ALPHA = 0x4D, EDGE_BOTTOM_ALPHA = 0x29;   // 0.30, 0.16
    private static final double DARKEN = 0.45, TOWARDS_WHITE = 0.72;

    private DockTint() {}

    /**
     * The tint wallpaper_tints gives the picture on screen, by its filename ("01-cap.jpg"), as an opaque colour; or
     * {@link #NONE} for no picture, no entry or a value that is not a colour.
     */
    static int of(Map<String, String> tints, String wallpaper) {
        return wallpaper == null || wallpaper.isEmpty() ? NONE : parse(tints.get(wallpaper));
    }

    /** "#RRGGBB", hex digits in either case, as 0xFFRRGGBB; {@link #NONE} for anything else, "#AARRGGBB" included. */
    static int parse(String value) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') return NONE;
        int rgb = 0;
        for (int i = 1; i < 7; i++) {
            char c = value.charAt(i);
            // ASCII only: Character.digit would also take full-width and other scripts' digits.
            int digit = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10
                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) return NONE;
            rgb = rgb << 4 | digit;
        }
        return 0xFF000000 | rgb;
    }

    /** The band's top: B at 42%. */
    static int bandTop(int base) {
        return BAND_TOP_ALPHA << 24 | base & 0xFFFFFF;
    }

    /** The band's bottom: each channel of B at 45%, at 56%; the hue stays, the tray just deepens. */
    static int bandBottom(int base) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            rgb |= (int) Math.round((base >> shift & 0xFF) * DARKEN) << shift;
        }
        return BAND_BOTTOM_ALPHA << 24 | rgb;
    }

    /** The edge line's top: B mixed 72% towards white, at 30%. */
    static int edgeTop(int base) {
        return EDGE_TOP_ALPHA << 24 | edge(base);
    }

    /** The edge line's bottom: the same colour as {@link #edgeTop}, at 16%. */
    static int edgeBottom(int base) {
        return EDGE_BOTTOM_ALPHA << 24 | edge(base);
    }

    static int darkBandTop(boolean translucent) {
        return translucent ? 0xA8252B35 : 0xE6252B35;
    }

    static int darkBandBottom(boolean translucent) {
        return translucent ? 0xB813171F : 0xF013171F;
    }

    static int darkEdgeTop() {
        return 0x38E2EAF6;
    }

    static int darkEdgeBottom() {
        return 0x12D2DEEE;
    }

    static int darkShadeBottom() {
        return 0x28000000;
    }

    /** One source-over layer interpolates every ARGB component, so its opacity never falls below that interpolation. */
    static int interpolate(int from, int to, float progress) {
        float t = progress < 0f ? 0f : Math.min(progress, 1f);
        return channel(from, to, 24, t) << 24 | channel(from, to, 16, t) << 16
                | channel(from, to, 8, t) << 8 | channel(from, to, 0, t);
    }

    private static int channel(int from, int to, int shift, float progress) {
        int first = from >> shift & 0xFF;
        int last = to >> shift & 0xFF;
        return Math.round(first + (last - first) * progress);
    }

    /** B mixed 72% towards white: c + (255 - c) * 0.72 for each of its red, green and blue. */
    private static int edge(int base) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = base >> shift & 0xFF;
            rgb |= (int) Math.round(c + (255 - c) * TOWARDS_WHITE) << shift;
        }
        return rgb;
    }
}
