package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Concentric dock curves and padding that remains within the available grid space. */
public class DockCornersTest {
    private static final float DENSITY = 2f;
    private static final int INNER = 1920 - 2 * 56 * 2 - 2 * 10 * 2;

    @Test public void bandAndTileShareTheirCornerCentre() {
        assertEquals(77f, DockDecoration.radiusPx(25f, 52f), 0f);
    }

    @Test public void paddingMatchesTheGapUntilAnEdgeLimitsIt() {
        assertEquals(50f, DockDecoration.evenPaddingPx(1000, 4, 200, 80f), .001f);
        assertEquals(30f, DockDecoration.evenPaddingPx(1000, 4, 200, 30f), .001f);
    }

    @Test public void tightOrMissingGeometryNeverProducesNegativePadding() {
        assertEquals(0f, DockDecoration.evenPaddingPx(1000, 0, 200, 30f), 0f);
        assertEquals(0f, DockDecoration.evenPaddingPx(1000, 4, 200, -10f), 0f);
        assertEquals(0f, DockDecoration.evenPaddingPx(100, 4, 200, 30f), 0f);
    }

    @Test public void supportedColumnCountsKeepTheirConcentricCurves() {
        for (int columns = 4; columns <= 7; columns++) {
            int tile = AppAdapter.tileWidthPx(INNER, columns, DENSITY);
            float gap = INNER / (float) columns - tile;
            float pad = DockDecoration.evenPaddingPx(INNER, columns, tile, Float.MAX_VALUE);
            assertTrue(pad >= 0);
            assertEquals(gap, pad, .001f);
            assertEquals(pad, DockDecoration.radiusPx(AppAdapter.cornerRadius(tile, DENSITY), pad)
                    - AppAdapter.cornerRadius(tile, DENSITY), .001f);
        }
    }
}
