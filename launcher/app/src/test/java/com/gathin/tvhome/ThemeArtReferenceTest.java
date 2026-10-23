package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeArtReferenceTest {
    @Test public void parsesSafeBannerAndAtlasReferences() {
        ThemeArtReference.Value plain = ThemeArtReference.parse("banners/island.png");
        ThemeArtReference.Value atlas = ThemeArtReference.parse("banners/hyrule.webp#15");
        assertNotNull(plain); assertEquals("banners/island.png", plain.path); assertEquals(-1, plain.cell);
        assertNotNull(atlas); assertEquals("banners/hyrule.webp", atlas.path); assertEquals(15, atlas.cell);
        assertFalse(atlas.fits(3, 128));
        assertFalse(atlas.fits(128, 3));
        assertTrue(atlas.fits(4, 4));
    }

    @Test public void parsesExplicitPixelRegionWithoutChangingAtlasSyntax() {
        ThemeArtReference.Value region = ThemeArtReference.parse("banners/characters.png#12,8,384,512");
        assertNotNull(region); assertEquals(-1, region.cell); assertTrue(region.hasRegion());
        assertEquals(12, region.x); assertEquals(8, region.y); assertEquals(384, region.width); assertEquals(512, region.height);
        assertTrue(region.fits(512, 1024));
        assertFalse(region.fits(395, 1024));
    }

    @Test public void rejectsTraversalNestedFilesAndBadAtlasCells() {
        assertNull(ThemeArtReference.parse("banners/../secret.png"));
        assertNull(ThemeArtReference.parse("banners/nested/card.png"));
        assertNull(ThemeArtReference.parse("wallpapers/card.png"));
        assertNull(ThemeArtReference.parse("banners/card.gif"));
        assertNull(ThemeArtReference.parse("banners/card.png#16"));
        assertNull(ThemeArtReference.parse("banners/card.png#1#2"));
        assertNull(ThemeArtReference.parse("banners/card.png#1,2,0,4"));
        assertNull(ThemeArtReference.parse("banners/card.png#1,2,4"));
        assertNull(ThemeArtReference.parse("banners/card.png#1, 2,4,5"));
        assertNull(ThemeArtReference.parse("banners/card.png#1,2,4097,5"));
    }

    @Test public void onlyAWholeOpaqueImageFillsTheCardEdgeToEdge() {
        // The Odyssey and soft-glow packs' banners: whole 384x216 WebPs with no alpha, drawn like bundled banners.
        assertTrue(ThemeArtReference.fullBleed(ThemeArtReference.parse("banners/iqiyi.webp"), false));
        // A transparent sprite, an atlas cell and a rectangle are fitted onto the theme's card ground instead.
        assertFalse(ThemeArtReference.fullBleed(ThemeArtReference.parse("banners/iqiyi.webp"), true));
        assertFalse(ThemeArtReference.fullBleed(ThemeArtReference.parse("banners/hyrule.webp#3"), false));
        assertFalse(ThemeArtReference.fullBleed(ThemeArtReference.parse("banners/island.webp#26,359,315,310"), false));
    }

    @Test public void boundsLimitsProtectDecoding() {
        assertTrue(ThemeArtReference.safeBounds(4096, 4096));
        assertFalse(ThemeArtReference.safeBounds(4097, 1));
        assertFalse(ThemeArtReference.safeBounds(4096, 4097));
        assertFalse(ThemeArtReference.safeBounds(0, 100));
    }

    @Test public void samplesDownToTheRequestedLongestSide() {
        assertEquals(1, ThemeArtReference.sampleFor(512, 512));
        assertEquals(2, ThemeArtReference.sampleFor(513, 512));
        assertEquals(8, ThemeArtReference.sampleFor(4096, 512));
        assertEquals(4, ThemeArtReference.sampleFor(3000, 768));
    }

    @Test public void atlasCellsCoverTheImageWithoutGoingOutOfBounds() {
        assertEquals(0, ThemeArtReference.cellLeft(101, 0));
        assertEquals(25, ThemeArtReference.cellRight(101, 0));
        assertEquals(75, ThemeArtReference.cellLeft(101, 3));
        assertEquals(101, ThemeArtReference.cellRight(101, 3));
        assertEquals(75, ThemeArtReference.cellTop(101, 15));
        assertEquals(101, ThemeArtReference.cellBottom(101, 15));
    }
}
