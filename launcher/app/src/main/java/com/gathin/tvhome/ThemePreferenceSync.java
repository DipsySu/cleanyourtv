package com.gathin.tvhome;

/**
 * Keeps the boundary between per-theme wallpaper selection and the user's global launcher choices.
 * Theme activation, imports and legacy pushes all change a theme signature, but must never reset
 * a layout or motion choice that the user made on the TV.
 */
final class ThemePreferenceSync {
    interface Store {
        String getThemeSignature();
        void writeThemeSignature(String signature, boolean clearWallpaperChoice);
    }

    private ThemePreferenceSync() { }

    /**
     * Records the active theme identity. A changed identity makes a previous wallpaper filename
     * unsafe to reuse, while columns, rows and motion remain global preferences. On a launcher
     * upgrade without a recorded identity, a legacy revision marker still clears that old
     * wallpaper selection once.
     */
    static void sync(Store store, String signature, boolean hasRevisionMarker) {
        String seen = store.getThemeSignature();
        if (signature.equals(seen)) return;
        store.writeThemeSignature(signature, seen != null || hasRevisionMarker);
    }
}
