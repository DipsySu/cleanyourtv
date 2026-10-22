package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class HeaderStyleTest {
    private static final Map<String, String> NAMES = Config.packageMap(Arrays.asList(
            "01-cap.jpg=帽子之国", " 08-moon.jpg = 月之国 ", "02-metro.jpg=", "=沙之国", "04-seaside.jpg"));

    @Test public void odysseyIsTheOnlyOtherStyle() {
        assertEquals(HeaderStyle.ODYSSEY, HeaderStyle.parse("odyssey"));
        assertEquals(HeaderStyle.ODYSSEY, HeaderStyle.parse(" odyssey "));
        assertEquals(HeaderStyle.DEFAULT, HeaderStyle.parse("default"));
    }

    @Test public void anUnknownStyleFallsBackToDefault() {
        assertEquals(HeaderStyle.DEFAULT, HeaderStyle.parse("hud"));
        assertEquals(HeaderStyle.DEFAULT, HeaderStyle.parse("Odyssey"));
        assertEquals(HeaderStyle.DEFAULT, HeaderStyle.parse(""));
        assertEquals(HeaderStyle.DEFAULT, HeaderStyle.parse(null));
    }

    @Test public void wallpaperNamesFollowThePackageMapRules() {
        assertEquals("帽子之国", HeaderStyle.kingdom(NAMES, "01-cap.jpg"));
        assertEquals("月之国", HeaderStyle.kingdom(NAMES, "08-moon.jpg"));
        // Entries without both halves are skipped, as for app_labels.
        assertEquals("", HeaderStyle.kingdom(NAMES, "02-metro.jpg"));
        assertEquals("", HeaderStyle.kingdom(NAMES, "04-seaside.jpg"));
        assertEquals(2, NAMES.size());
    }

    @Test public void noPictureOrAnUnnamedOneHasNoKingdom() {
        assertEquals("", HeaderStyle.kingdom(NAMES, ""));
        assertEquals("", HeaderStyle.kingdom(NAMES, null));
        assertEquals("", HeaderStyle.kingdom(NAMES, "wallpaper.jpg"));
        assertEquals("", HeaderStyle.kingdom(NAMES, "01-CAP.JPG"));
        assertEquals("", HeaderStyle.kingdom(Collections.emptyMap(), "01-cap.jpg"));
    }

    @Test public void theKingdomGoesBeforeTheGreeting() {
        assertEquals("帽子之国 · 下午好", HeaderStyle.greeting("帽子之国", "下午好"));
        assertEquals("Cap Kingdom · Good morning, Mr. Trịnh",
                HeaderStyle.greeting("Cap Kingdom", "Good morning, Mr. Trịnh"));
    }

    @Test public void withoutAKingdomTheGreetingIsUnchanged() {
        assertEquals("下午好", HeaderStyle.greeting("", "下午好"));
        assertEquals("Happy Diwali", HeaderStyle.greeting(null, "Happy Diwali"));
        assertEquals("下午好", HeaderStyle.greeting(HeaderStyle.kingdom(NAMES, "05-wooded.jpg"), "下午好"));
    }
}
