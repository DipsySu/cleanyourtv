package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DockTintTest {
    /** The Odyssey theme's wallpaper_tints and the ARGB the tints were chosen with: band top, bottom, edge top, bottom. */
    private static final Object[][] KINGDOMS = {
            {"01-cap.jpg", "#454C87", 0x6B454C87, 0x8F1F223D, 0x4DCBCDDD, 0x29CBCDDD},
            {"02-metro.jpg", "#743A82", 0x6B743A82, 0x8F341A3B, 0x4DD8C8DC, 0x29D8C8DC},
            {"03-sand.jpg", "#9E5F2E", 0x6B9E5F2E, 0x8F472B15, 0x4DE4D2C4, 0x29E4D2C4},
            {"04-seaside.jpg", "#2A7B7E", 0x6B2A7B7E, 0x8F133739, 0x4DC3DADB, 0x29C3DADB},
            {"05-wooded.jpg", "#367254", 0x6B367254, 0x8F183326, 0x4DC7D8CF, 0x29C7D8CF},
            {"06-lost.jpg", "#86376C", 0x6B86376C, 0x8F3C1931, 0x4DDDC7D6, 0x29DDC7D6},
            {"07-luncheon.jpg", "#8A2E40", 0x6B8A2E40, 0x8F3E151D, 0x4DDEC4CA, 0x29DEC4CA},
            {"08-moon.jpg", "#306691", 0x6B306691, 0x8F162E41, 0x4DC5D4E0, 0x29C5D4E0},
    };

    @Test public void theKingdomTintsGiveTheChosenTrayColours() {
        for (Object[] k : KINGDOMS) {
            int base = DockTint.parse((String) k[1]);
            String name = (String) k[0];
            assertEquals(name, (int) k[2], DockTint.bandTop(base));
            assertEquals(name, (int) k[3], DockTint.bandBottom(base));
            assertEquals(name, (int) k[4], DockTint.edgeTop(base));
            assertEquals(name, (int) k[5], DockTint.edgeBottom(base));
        }
    }

    @Test public void theRecipeHoldsAtTheEndsOfTheRange() {
        assertEquals(0x6B000000, DockTint.bandTop(0xFF000000));
        assertEquals(0x8F000000, DockTint.bandBottom(0xFF000000));
        assertEquals(0x4DB8B8B8, DockTint.edgeTop(0xFF000000));   // 0 + 255 * 0.72 = 183.6
        assertEquals(0x8F737373, DockTint.bandBottom(0xFFFFFFFF));   // 255 * 0.45 = 114.75
        assertEquals(0x29FFFFFF, DockTint.edgeBottom(0xFFFFFFFF));
    }

    @Test public void aColourIsHashAndSixHexDigits() {
        assertEquals(0xFF454C87, DockTint.parse("#454C87"));
        assertEquals(0xFF2A7B7E, DockTint.parse("#2a7b7e"));
        assertEquals(0xFF000000, DockTint.parse("#000000"));   // black is a tint, not "none"
        assertEquals(0xFFFFFFFF, DockTint.parse("#FFFFFF"));
    }

    @Test public void anythingElseIsNoTint() {
        for (String bad : new String[]{null, "", "#", "454C87", "#454C8", "#454C877", "#80454C87", "#45 4C87",
                "#454G87", "red", "0x454C87", "##454C8", "#+54C87", "#-54C87", "#４５４Ｃ８７"}) {
            assertEquals(String.valueOf(bad), DockTint.NONE, DockTint.parse(bad));
        }
    }

    @Test public void theTintFollowsTheWallpaperOnScreen() {
        Map<String, String> tints = Config.packageMap(Arrays.asList(
                "01-cap.jpg=#454C87", " 08-moon.jpg = #306691 ", "02-metro.jpg=purple", "03-sand.jpg=#9E5F2E=x",
                "=#2A7B7E", "05-wooded.jpg"));
        assertEquals(0xFF454C87, DockTint.of(tints, "01-cap.jpg"));
        assertEquals(0xFF306691, DockTint.of(tints, "08-moon.jpg"));
        // A value that is not a colour, an '=' kept in the value, and entries without both halves: no tint.
        assertEquals(DockTint.NONE, DockTint.of(tints, "02-metro.jpg"));
        assertEquals(DockTint.NONE, DockTint.of(tints, "03-sand.jpg"));
        assertEquals(DockTint.NONE, DockTint.of(tints, "04-seaside.jpg"));
        assertEquals(DockTint.NONE, DockTint.of(tints, "05-wooded.jpg"));
    }

    @Test public void noPictureOrAnUnknownOneHasNoTint() {
        Map<String, String> tints = Config.packageMap(Collections.singletonList("01-cap.jpg=#454C87"));
        assertEquals(DockTint.NONE, DockTint.of(tints, ""));
        assertEquals(DockTint.NONE, DockTint.of(tints, null));
        assertEquals(DockTint.NONE, DockTint.of(tints, "wallpaper.jpg"));
        assertEquals(DockTint.NONE, DockTint.of(tints, "01-CAP.JPG"));   // filenames match exactly, as for names
        assertEquals(DockTint.NONE, DockTint.of(Collections.emptyMap(), "01-cap.jpg"));
    }

    @Test public void oneLayerGradientInterpolatesEveryArgbChannelWithoutAnAlphaDip() {
        int tintedTop = DockTint.bandTop(DockTint.parse("#454C87"));
        int darkTop = DockTint.darkBandTop(false);
        assertEquals(tintedTop, DockTint.interpolate(tintedTop, darkTop, 0f));
        assertEquals(darkTop, DockTint.interpolate(tintedTop, darkTop, 1f));
        // One gradient at half progress is the ARGB midpoint (0x6B to 0xE6), rather than two translucent
        // source-over layers whose combined alpha would be lower than either intended midpoint.
        assertEquals(0xA9353C5E, DockTint.interpolate(tintedTop, darkTop, .5f));
    }

    @Test public void rotatingBetweenKingdomsKeepsTheTrayOpacityConstantAtEveryStep() {
        int from = DockTint.parse("#454C87"), to = DockTint.parse("#9E5F2E");
        for (int step = 0; step <= 100; step++) {
            float progress = step / 100f;
            assertEquals(0x6B, DockTint.interpolate(DockTint.bandTop(from), DockTint.bandTop(to), progress) >>> 24);
            assertEquals(0x8F, DockTint.interpolate(DockTint.bandBottom(from), DockTint.bandBottom(to), progress) >>> 24);
        }
    }

    @Test public void tintAndNoneAlsoInterpolateTheBottomShade() {
        assertEquals(0x00000000, DockTint.interpolate(0, DockTint.darkShadeBottom(), 0f));
        assertEquals(0x14000000, DockTint.interpolate(0, DockTint.darkShadeBottom(), .5f));
        assertEquals(DockTint.darkShadeBottom(), DockTint.interpolate(0, DockTint.darkShadeBottom(), 1f));
        assertEquals(0xA8252B35, DockTint.darkBandTop(true));
        assertEquals(0xE6252B35, DockTint.darkBandTop(false));
    }

    @Test public void interruptedTintTransitionCanAlwaysLandOnItsExactTarget() {
        int from = DockTint.bandBottom(DockTint.parse("#454C87"));
        int to = DockTint.darkBandBottom(false);
        int interruptedFrame = DockTint.interpolate(from, to, .31f);
        assertEquals(to, DockTint.interpolate(interruptedFrame, to, 1f));
        // DockDecoration.endFade() uses the original target at 1, which avoids retaining a rounded intermediate.
        assertEquals(to, DockTint.interpolate(from, to, 1f));
    }
}
