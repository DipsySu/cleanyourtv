package com.gathin.tvhome;

import android.graphics.PixelFormat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThemeArtworkTest {
    @Test public void aFullBleedCardIsOpaqueSoTheTileBackgroundUnderItIsDropped() {
        // An opaque, non-fit pack banner (the Odyssey WebPs) reports OPAQUE, as the bundled WebPs did before it.
        assertEquals(PixelFormat.OPAQUE, ThemeArtwork.opacity(false, 255));
    }

    @Test public void aFittedOrFadedCardStaysTranslucent() {
        // A fitted sprite shows the card ground around it, and a faded card shows what is behind it.
        assertEquals(PixelFormat.TRANSLUCENT, ThemeArtwork.opacity(true, 255));
        assertEquals(PixelFormat.TRANSLUCENT, ThemeArtwork.opacity(false, 254));
        assertEquals(PixelFormat.TRANSLUCENT, ThemeArtwork.opacity(true, 0));
    }
}
