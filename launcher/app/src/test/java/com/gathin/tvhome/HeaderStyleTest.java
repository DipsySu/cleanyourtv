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

    private static final Map<String, String> COINS = Config.packageMap(Arrays.asList(
            "01-cap.jpg=coin_odyssey_cap", " 08-moon.jpg = coin_odyssey_moon ", "02-metro.jpg=ic_odyssey_coin",
            "03-sand.jpg=banner_odyssey_live", "04-seaside.jpg=Coin_Odyssey_Seaside", "05-wooded.jpg=COIN_ODYSSEY_WOODED",
            "06-lost.jpg=coin_odyssey_lost=x", "07-luncheon.jpg=coin_", "a.jpg=coin_odyssey cap",
            "b.jpg=coin_x:drawable/banner_odyssey_live", "c.jpg=coin_../banner", "d.jpg=", "=coin_odyssey_cap",
            "e.jpg=coin_odyssey_unknown"));

    @Test public void theCoinFollowsTheWallpaperOnScreen() {
        assertEquals("coin_odyssey_cap", HeaderStyle.coin(COINS, "01-cap.jpg"));
        assertEquals("coin_odyssey_moon", HeaderStyle.coin(COINS, "08-moon.jpg"));
        // Only a resource name is checked here: whether the APK has it is MainActivity's lookup (the vector if not).
        assertEquals("coin_odyssey_unknown", HeaderStyle.coin(COINS, "e.jpg"));
    }

    @Test public void theVectorCoinOrANameThatIsNotACoinGivesTheVectorCoin() {
        assertEquals("", HeaderStyle.coin(COINS, "02-metro.jpg"));   // ic_odyssey_coin: the vector, by name
        assertEquals("", HeaderStyle.coin(COINS, "03-sand.jpg"));    // a banner is not a coin
        // Resource names are lower case: no case folding.
        assertEquals("", HeaderStyle.coin(COINS, "04-seaside.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "05-wooded.jpg"));
        // The value keeps an '=' (package-map rules), which no resource name has.
        assertEquals("", HeaderStyle.coin(COINS, "06-lost.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "07-luncheon.jpg"));   // the prefix alone
        assertEquals("", HeaderStyle.coin(COINS, "a.jpg"));
        // Nothing getIdentifier would read as another package or type.
        assertEquals("", HeaderStyle.coin(COINS, "b.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "c.jpg"));
    }

    @Test public void noPictureOrAnUnlistedOneHasTheVectorCoin() {
        assertEquals("", HeaderStyle.coin(COINS, ""));
        assertEquals("", HeaderStyle.coin(COINS, null));
        assertEquals("", HeaderStyle.coin(COINS, "d.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "wallpaper.jpg"));
        assertEquals("", HeaderStyle.coin(COINS, "01-CAP.JPG"));
        assertEquals("", HeaderStyle.coin(Collections.emptyMap(), "01-cap.jpg"));
    }
}
