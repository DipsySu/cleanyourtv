package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WallpaperFadeTest {

    @Test public void theFadeIsOffWithAnimationsOffOrReducedMotion() {
        assertEquals(1200, WallpaperFade.duration(true, false));
        assertEquals(0, WallpaperFade.duration(false, false));
        assertEquals(0, WallpaperFade.duration(true, true));
        assertEquals(0, WallpaperFade.duration(false, true));
    }

    @Test public void theCurveDeceleratesFromNoneToAll() {
        assertEquals(0f, WallpaperFade.eased(0f), 0f);
        assertEquals(0.75f, WallpaperFade.eased(0.5f), 1e-6f);   // three quarters in at half the time
        assertEquals(1f, WallpaperFade.eased(1f), 0f);
        assertEquals(0f, WallpaperFade.eased(-0.2f), 0f);
        assertEquals(1f, WallpaperFade.eased(1.3f), 0f);
        float last = 0f, lastStep = Float.MAX_VALUE;
        for (int i = 1; i <= 100; i++) {
            float value = WallpaperFade.eased(i / 100f);
            assertTrue("rises at " + i, value > last);
            assertTrue("slows at " + i, value - last <= lastStep + 1e-6f);
            lastStep = value - last;
            last = value;
        }
    }

    @Test public void theHeaderSwitchesWhenTheNewPictureIsHalfIn() {
        assertEquals(351, WallpaperFade.halfway(1200));
        assertEquals(0.5f, WallpaperFade.eased(WallpaperFade.halfway(1200) / 1200f), 0.001f);
        assertEquals(0.5f, WallpaperFade.eased(WallpaperFade.halfway(10_000) / 10_000f), 0.0001f);
        assertEquals(0, WallpaperFade.halfway(0));
    }

    @Test public void theTwoTraysAlphasAlwaysAddUpToOpaque() {
        assertEquals(0, WallpaperFade.inAlpha(0f));
        assertEquals(255, WallpaperFade.outAlpha(0f));
        assertEquals(255, WallpaperFade.inAlpha(1f));
        assertEquals(0, WallpaperFade.outAlpha(1f));
        assertEquals(128, WallpaperFade.inAlpha(0.5f));   // 127.5 rounds up
        assertEquals(127, WallpaperFade.outAlpha(0.5f));
        assertEquals(0, WallpaperFade.inAlpha(-1f));
        assertEquals(255, WallpaperFade.inAlpha(2f));
        int last = -1;
        for (int i = 0; i <= 1000; i++) {
            float eased = WallpaperFade.eased(i / 1000f);
            int in = WallpaperFade.inAlpha(eased);
            assertEquals(255, in + WallpaperFade.outAlpha(eased));
            assertTrue(in >= last);
            last = in;
        }
    }
}
