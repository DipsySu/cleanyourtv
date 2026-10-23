package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeaderStyleTest {
    private static final Map<String, String> NAMES = Config.packageMap(Arrays.asList(
            "01-cap.jpg=帽子之国", " 08-moon.jpg = 月之国 ", "02-metro.jpg=", "=沙之国", "04-seaside.jpg"));

    @Test public void knownGameStylesParseWithoutChangingOdyssey() {
        assertEquals(HeaderStyle.ODYSSEY, HeaderStyle.parse("odyssey"));
        assertEquals(HeaderStyle.ODYSSEY, HeaderStyle.parse(" odyssey "));
        assertEquals(HeaderStyle.ANIMAL_CROSSING, HeaderStyle.parse("animal_crossing"));
        assertEquals(HeaderStyle.ANIMAL_CROSSING, HeaderStyle.parse(" animal_crossing "));
        assertEquals(HeaderStyle.ZELDA, HeaderStyle.parse("zelda"));
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

    private static final Map<String, String> COINS = Config.packageMap(Arrays.asList(
            "01-cap.jpg=icons/coin-cap.webp", " 08-moon.jpg = icons/coin-moon.webp ", "03-sand.jpg=icons/Coin_Sand.WEBP",
            "04-seaside.jpg=icons/coin.seaside.png", "05-wooded.jpg=icons/coin-wooded.jpeg",
            // The resource names configs used while the coins were in the APK.
            "02-metro.jpg=ic_odyssey_coin", "06-lost.jpg=coin_odyssey_lost", "07-luncheon.jpg=banner_odyssey_live",
            // Traversal, suffixes, nesting, other folders and other files.
            "a.jpg=icons/../banners/cast.webp", "b.jpg=icons/..coin.webp", "c.jpg=icons/coin-cap.webp#3",
            "d.jpg=icons/coin-cap.webp#0,0,64,64", "e.jpg=icons/kingdoms/coin-cap.webp", "f.jpg=banners/coin-cap.webp",
            "g.jpg=Icons/coin-cap.webp", "h.jpg=/icons/coin-cap.webp", "i.jpg=icons\\coin-cap.webp",
            "j.jpg=icons/coin-cap.gif", "k.jpg=icons/", "l.jpg=icons/.coin.webp", "m.jpg=icons/coin cap.webp",
            "n.jpg=fonts/fredoka.ttf", "o.jpg=icons/coin-cap.webp=x", "p.jpg=", "=icons/coin-cap.webp"));

    @Test public void theCoinFollowsTheWallpaperOnScreen() {
        assertEquals("icons/coin-cap.webp", HeaderStyle.coin(COINS, "01-cap.jpg"));
        assertEquals("icons/coin-moon.webp", HeaderStyle.coin(COINS, "08-moon.jpg"));
        // Only the path is checked here: whether the file is there and decodes is OdysseyHeader.coin's (the vector if not).
        assertEquals("icons/coin.seaside.png", HeaderStyle.coin(COINS, "04-seaside.jpg"));
        assertEquals("icons/coin-wooded.jpeg", HeaderStyle.coin(COINS, "05-wooded.jpg"));
    }

    @Test public void theExtensionIsAnyCaseAsInTheZipRules() {
        assertEquals("icons/Coin_Sand.WEBP", HeaderStyle.coin(COINS, "03-sand.jpg"));
        assertTrue(HeaderStyle.coinPath("icons/COIN.PNG"));
    }

    @Test public void theOldResourceNamesGiveTheVectorCoin() {
        assertEquals("", HeaderStyle.coin(COINS, "02-metro.jpg"));      // ic_odyssey_coin: the vector, by name
        assertEquals("", HeaderStyle.coin(COINS, "06-lost.jpg"));       // no coin_* drawable ships any more
        assertEquals("", HeaderStyle.coin(COINS, "07-luncheon.jpg"));   // a banner is not a coin either
    }

    @Test public void onlyAnImageDirectlyInIconsIsACoin() {
        assertEquals("", HeaderStyle.coin(COINS, "a.jpg"));   // traversal
        assertEquals("", HeaderStyle.coin(COINS, "b.jpg"));   // '..' anywhere, as the ZIP rules refuse it
        assertEquals("", HeaderStyle.coin(COINS, "c.jpg"));   // a banner's atlas cell suffix
        assertEquals("", HeaderStyle.coin(COINS, "d.jpg"));   // a banner's region suffix
        assertEquals("", HeaderStyle.coin(COINS, "e.jpg"));   // nested
        assertEquals("", HeaderStyle.coin(COINS, "f.jpg"));   // another folder
        assertEquals("", HeaderStyle.coin(COINS, "g.jpg"));   // the folder's name is exact
        assertEquals("", HeaderStyle.coin(COINS, "h.jpg"));   // absolute
        assertEquals("", HeaderStyle.coin(COINS, "i.jpg"));   // a backslash
        assertEquals("", HeaderStyle.coin(COINS, "j.jpg"));   // not an image the pack may hold
        assertEquals("", HeaderStyle.coin(COINS, "k.jpg"));   // the folder alone
        assertEquals("", HeaderStyle.coin(COINS, "l.jpg"));   // a name must start with a letter or digit
        assertEquals("", HeaderStyle.coin(COINS, "m.jpg"));   // a space
        assertEquals("", HeaderStyle.coin(COINS, "n.jpg"));   // a font
        // The value keeps an '=' (package-map rules), which no file name has.
        assertEquals("", HeaderStyle.coin(COINS, "o.jpg"));
    }

    @Test public void noPictureOrAnUnlistedOneHasTheVectorCoin() {
        assertEquals("", HeaderStyle.coin(COINS, ""));
        assertEquals("", HeaderStyle.coin(COINS, null));
        assertEquals("", HeaderStyle.coin(COINS, "p.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "wallpaper.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "01-CAP.JPG"));
        assertEquals("", HeaderStyle.coin(Collections.emptyMap(), "01-cap.jpg"));
        assertFalse(HeaderStyle.coinPath(null));
    }
}
