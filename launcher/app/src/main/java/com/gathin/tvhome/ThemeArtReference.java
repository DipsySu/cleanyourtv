package com.gathin.tvhome;

/** Small Android-free rules for theme banner references and their bounded bitmap decodes. */
final class ThemeArtReference {
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    static final int MAX_SIDE = 4096;
    static final long MAX_PIXELS = 16L * 1024 * 1024;
    static final int ATLAS_COLUMNS = 4;
    static final int ATLAS_CELLS = ATLAS_COLUMNS * ATLAS_COLUMNS;

    static final class Value {
        final String path;
        final int cell;
        final int x, y, width, height;
        Value(String path, int cell, int x, int y, int width, int height) {
            this.path = path; this.cell = cell; this.x = x; this.y = y; this.width = width; this.height = height;
        }
        boolean hasRegion() { return width > 0 && height > 0; }
        boolean fits(int imageWidth, int imageHeight) {
            if (cell >= 0 && (imageWidth < ATLAS_COLUMNS || imageHeight < ATLAS_COLUMNS)) return false;
            return !hasRegion() || (x >= 0 && y >= 0 && (long) x + width <= imageWidth && (long) y + height <= imageHeight);
        }
    }

    private ThemeArtReference() {}

    /** Accepts a single safe image directly below banners/, optionally followed by #0 through #15. */
    static Value parse(String raw) {
        if (raw == null) return null;
        int hash = raw.indexOf('#');
        if (hash != raw.lastIndexOf('#')) return null;
        String path = hash < 0 ? raw : raw.substring(0, hash);
        int cell = -1, x = -1, y = -1, width = -1, height = -1;
        if (hash >= 0) {
            String suffix = raw.substring(hash + 1);
            if (suffix.matches("(?:[0-9]|1[0-5])")) {
                cell = Integer.parseInt(suffix);
            } else {
                String[] parts = suffix.split(",", -1);
                if (parts.length != 4) return null;
                int[] values = new int[4];
                for (int i = 0; i < values.length; i++) {
                    values[i] = decimal(parts[i]);
                    if (values[i] < 0) return null;
                }
                x = values[0]; y = values[1]; width = values[2]; height = values[3];
                if (width == 0 || height == 0) return null;
            }
        }
        String prefix = "banners/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) return null;
        String leaf = path.substring(prefix.length());
        if (leaf.indexOf('/') >= 0 || leaf.indexOf('\\') >= 0 || !leaf.matches("[A-Za-z0-9][A-Za-z0-9_.-]*\\.(?i:png|webp|jpg|jpeg)")) return null;
        return new Value(path, cell, x, y, width, height);
    }

    private static int decimal(String value) {
        if (!value.matches("[0-9]+")) return -1;
        try {
            long parsed = Long.parseLong(value);
            return parsed <= MAX_SIDE ? (int) parsed : -1;
        } catch (NumberFormatException ignored) { return -1; }
    }

    static boolean safeBounds(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE && (long) width * height <= MAX_PIXELS;
    }

    /** A power-of-two BitmapFactory sample whose decoded longest side does not exceed {@code cap}. */
    static int sampleFor(int longest, int cap) {
        if (longest <= 0 || cap <= 0) return 1;
        int sample = 1;
        while ((longest + sample - 1) / sample > cap && sample <= (1 << 29)) sample <<= 1;
        return sample;
    }

    static int cellLeft(int width, int cell) { return width * (cell % ATLAS_COLUMNS) / ATLAS_COLUMNS; }
    static int cellTop(int height, int cell) { return height * (cell / ATLAS_COLUMNS) / ATLAS_COLUMNS; }
    static int cellRight(int width, int cell) { return width * ((cell % ATLAS_COLUMNS) + 1) / ATLAS_COLUMNS; }
    static int cellBottom(int height, int cell) { return height * ((cell / ATLAS_COLUMNS) + 1) / ATLAS_COLUMNS; }
}
