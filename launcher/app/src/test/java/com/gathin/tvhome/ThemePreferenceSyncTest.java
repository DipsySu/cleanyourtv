package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemePreferenceSyncTest {
    @Test public void themeSwitchesKeepGlobalLayoutAndMotionChoices() {
        FakeStore store = overrides("animal@1:config-a:absent", "animal-wallpaper");

        ThemePreferenceSync.sync(store, "zelda@2:config-z:absent", false);
        assertOverridesRemain(store);
        assertFalse(store.values.containsKey("wallpaper_choice"));

        store.values.put("wallpaper_choice", "zelda-wallpaper");
        ThemePreferenceSync.sync(store, "zelda@3:config-z:absent", false); // same package activated again
        assertOverridesRemain(store);
        assertFalse(store.values.containsKey("wallpaper_choice"));

        store.values.put("wallpaper_choice", "zelda-new-wallpaper");
        ThemePreferenceSync.sync(store, "zelda@3:config-v2:absent", false); // imported newer package
        assertOverridesRemain(store);
        assertFalse(store.values.containsKey("wallpaper_choice"));

        store.values.put("wallpaper_choice", "legacy-wallpaper");
        ThemePreferenceSync.sync(store, "legacy-config:push-uuid", true); // legacy push or reset
        assertOverridesRemain(store);
        assertFalse(store.values.containsKey("wallpaper_choice"));

        ThemePreferenceSync.sync(store, "animal@4:config-a:absent", false); // switch back to Animal
        assertOverridesRemain(store);
        assertFalse(store.values.containsKey("wallpaper_choice"));
    }

    @Test public void firstSignaturePreservesOverridesAndOnlyLegacyMarkerClearsWallpaper() {
        FakeStore marked = overrides(null, "old-wallpaper");
        ThemePreferenceSync.sync(marked, "legacy-config:push-uuid", true);
        assertOverridesRemain(marked);
        assertFalse(marked.values.containsKey("wallpaper_choice"));

        FakeStore unmarked = overrides(null, "old-wallpaper");
        ThemePreferenceSync.sync(unmarked, "legacy-config:absent", false);
        assertOverridesRemain(unmarked);
        assertEquals("old-wallpaper", unmarked.values.get("wallpaper_choice"));
    }

    @Test public void ordinaryLoadDoesNotWriteOrCreateOverrides() {
        FakeStore store = new FakeStore();
        store.values.put("theme_signature", "zelda@3:config:absent");
        store.values.put("wallpaper_choice", "sunset.webp");
        ThemePreferenceSync.sync(store, "zelda@3:config:absent", false);

        assertEquals(0, store.writes);
        assertEquals("sunset.webp", store.values.get("wallpaper_choice"));
        assertFalse(store.values.containsKey("columns"));
        assertFalse(store.values.containsKey("rows_below_dock"));
        assertFalse(store.values.containsKey("animations"));
        assertFalse(store.values.containsKey("advanced_effects"));
    }

    @Test public void signatureChangeWithoutOverridesOnlyUpdatesSignature() {
        FakeStore store = new FakeStore();
        store.values.put("theme_signature", "animal@1:config-a:absent");

        ThemePreferenceSync.sync(store, "zelda@2:config-z:absent", false);
        assertEquals(1, store.writes);
        assertEquals("zelda@2:config-z:absent", store.values.get("theme_signature"));
        assertEquals(1, store.values.size());
        assertFalse(store.values.containsKey("columns"));
        assertFalse(store.values.containsKey("rows_below_dock"));
        assertFalse(store.values.containsKey("animations"));
        assertFalse(store.values.containsKey("advanced_effects"));

        ThemePreferenceSync.sync(store, "zelda@2:config-z:absent", false);
        assertEquals(1, store.writes);
    }

    private static FakeStore overrides(String signature, String wallpaper) {
        FakeStore store = new FakeStore();
        if (signature != null) store.values.put("theme_signature", signature);
        store.values.put("columns", 4);
        store.values.put("rows_below_dock", 0);
        store.values.put("animations", false);
        store.values.put("advanced_effects", false);
        store.values.put("wallpaper_choice", wallpaper);
        return store;
    }

    private static void assertOverridesRemain(FakeStore store) {
        assertEquals(4, store.values.get("columns"));
        assertEquals(0, store.values.get("rows_below_dock"));
        assertEquals(false, store.values.get("animations"));
        assertEquals(false, store.values.get("advanced_effects"));
    }

    private static final class FakeStore implements ThemePreferenceSync.Store {
        final Map<String, Object> values = new HashMap<>();
        int writes;

        @Override public String getThemeSignature() { return (String) values.get("theme_signature"); }

        @Override public void writeThemeSignature(String signature, boolean clearWallpaperChoice) {
            writes++;
            if (clearWallpaperChoice) values.remove("wallpaper_choice");
            values.put("theme_signature", signature);
        }
    }
}
