package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WallpaperPickerChoicesTest {
    @Test public void selectionReservesThemeAndOffRows() {
        assertEquals(0, WallpaperPickerChoices.selectedIndex(Arrays.asList("a.jpg"), null));
        assertEquals(1, WallpaperPickerChoices.selectedIndex(Arrays.asList("a.jpg"), "none"));
        assertEquals(2, WallpaperPickerChoices.selectedIndex(Arrays.asList("a.jpg"), "a.jpg"));
        assertEquals(-1, WallpaperPickerChoices.selectedIndex(Arrays.asList("a.jpg"), "removed.jpg"));
    }

    @Test public void displayNameUsesThemeNameThenFilename() {
        Map<String, String> names = new HashMap<>();
        names.put("a.jpg", "帽子之国");
        names.put("blank.jpg", " ");
        assertEquals("帽子之国", WallpaperPickerChoices.displayName("a.jpg", names));
        assertEquals("blank.jpg", WallpaperPickerChoices.displayName("blank.jpg", names));
        assertEquals("missing.jpg", WallpaperPickerChoices.displayName("missing.jpg", Collections.emptyMap()));
    }
}
