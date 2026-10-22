package com.gathin.tvhome;

import java.util.List;
import java.util.Map;

/** Pure selection and naming rules for the on-TV wallpaper picker. */
final class WallpaperPickerChoices {
    static final String NONE = "none";

    private WallpaperPickerChoices() {}

    /** The first two rows are always theme rotation and no wallpaper. */
    static int selectedIndex(List<String> filenames, String choice) {
        if (choice == null) return 0;
        if (NONE.equals(choice)) return 1;
        for (int i = 0; i < filenames.size(); i++) {
            if (choice.equals(filenames.get(i))) return i + 2;
        }
        // A removed image should not make a misleading row appear selected.
        return -1;
    }

    /** A theme may omit a display name; the filename remains a useful fallback. */
    static String displayName(String filename, Map<String, String> names) {
        String name = names == null ? null : names.get(filename);
        return name == null || name.trim().isEmpty() ? filename : name;
    }
}
