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

}
