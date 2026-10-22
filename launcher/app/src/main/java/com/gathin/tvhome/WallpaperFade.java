package com.gathin.tvhome;

/**
 * The crossfade a rotated wallpaper comes in with (WallpaperPlayer), and what follows it: the Odyssey header's kingdom
 * and coin switch when the new picture is half in, and a tinted dock tray fades with it (DockDecoration). Pure timing
 * and alpha arithmetic with no Android types, so the JVM tests hold every part to the same curve.
 *
 * The new picture's alpha follows {@link #eased}, DecelerateInterpolator's curve (factor 1): most of the change comes
 * early and it settles softly. The picture Home opens with, an on-TV wallpaper pick (it recreates Home) and Home with
 * animations off or reduced_motion on still cut straight to the picture.
 */
final class WallpaperFade {
    /** How long a rotated picture takes to fade in over the one before it. */
    static final long DURATION_MS = 1200;

    private WallpaperFade() {}

    /** {@link #DURATION_MS}, or 0 (a cut) with animations off or reduced_motion on, as Home's other motion is gated. */
    static long duration(boolean animations, boolean reducedMotion) {
        return animations && !reducedMotion ? DURATION_MS : 0;
    }

    /** How far in the new picture is at {@code fraction} of the fade's time: 1 - (1 - t)², t kept within 0 to 1. */
    static float eased(float fraction) {
        float rest = 1f - clamp(fraction);
        return 1f - rest * rest;
    }

    /**
     * When the new picture is half in, in ms after the fade starts: {@link #eased} reaches 0.5 at 1 - √½ of the way,
     * 351 ms of 1200. The header switches its kingdom and coin then, so they change as the picture visibly does.
     */
    static long halfway(long duration) {
        return Math.round(duration * (1 - Math.sqrt(0.5)));
    }

    /** The incoming layer's alpha, 0 to 255, for an eased fraction from {@link #eased}. */
    static int inAlpha(float eased) {
        return Math.round(clamp(eased) * 255);
    }

    /** The outgoing layer's alpha: whatever {@link #inAlpha} leaves, so the two always add up to 255. */
    static int outAlpha(float eased) {
        return 255 - inAlpha(eased);
    }

    private static float clamp(float fraction) {
        return fraction < 0f ? 0f : Math.min(fraction, 1f);
    }
}
