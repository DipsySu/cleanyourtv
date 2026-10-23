package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Private, versioned store for portable theme packages. It deliberately has no storage permission. */
final class ThemeStore {
    private static final Object LOCK = new Object();
    private static final String PREFS = "theme_packages";
    private static final String ACTIVE = "active_id";
    private static final String REVISION = "revision";
    private static final String LEGACY = "legacy";
    private static final String ROOT = "theme-packs";
    private static final Set<String> VISUAL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "header_style", "background_color", "wallpaper", "wallpaper_rotation_minutes", "wallpaper_order",
            "wallpaper_dim", "wallpaper_names", "wallpaper_coins", "wallpaper_tints", "app_banners", "columns",
            "dock_columns", "rows_below_dock", "dock_opacity", "dock_glass", "dock_title", "dock_row",
            "dock_even_padding", "ambient_glow", "app_glow", "cinematic_home", "sky_intensity", "focus_overshoot",
            "always_show_names", "reduced_motion", "wallpaper_true_color", "clock_font", "clock_font_axes")));

    static final class Entry {
        final String id, name;
        final int version;
        final File directory;
        Entry(String id, String name, int version, File directory) {
            this.id = id; this.name = name; this.version = version; this.directory = directory;
        }
    }

    static final class Snapshot {
        final File directory;
        final JSONObject overlay;
        final String identity;
        final long revision;
        Snapshot(File directory, JSONObject overlay, String identity, long revision) {
            this.directory = directory; this.overlay = overlay; this.identity = identity; this.revision = revision;
        }
    }

    private ThemeStore() {}

    static List<Entry> list(Context context) {
        synchronized (LOCK) {
            ArrayList<Entry> entries = new ArrayList<>();
            entries.add(new Entry(LEGACY, context.getString(R.string.theme_legacy_name), 0, Config.dir(context)));
            File root = root(context);
            File[] ids = root.listFiles(File::isDirectory);
            if (ids != null) for (File idDir : ids) {
                String published = prefs(context).getString("latest." + idDir.getName(), null);
                Entry entry = publishedEntry(idDir, published);
                if (entry != null) entries.add(entry);
            }
            entries.subList(1, entries.size()).sort(Comparator.comparing(e -> e.name));
            return entries;
        }
    }

    static String activeId(Context context) { return prefs(context).getString(ACTIVE, LEGACY); }

    static File activeDir(Context context) {
        synchronized (LOCK) {
            String active = activeId(context);
            if (LEGACY.equals(active)) return Config.dir(context);
            Entry entry = publishedEntry(new File(root(context), active), prefs(context).getString("latest." + active, null));
            return entry == null ? Config.dir(context) : entry.directory;
        }
    }

    static long revision(Context context) { return prefs(context).getLong(REVISION, 0L); }
    static String identity(Context context) { return activeId(context) + "@" + revision(context); }

    static Snapshot snapshot(Context context) {
        final String active;
        final long currentRevision;
        final File directory;
        final boolean available;
        synchronized (LOCK) {
            active = activeId(context);
            currentRevision = revision(context);
            if (LEGACY.equals(active)) { directory = Config.dir(context); available = true; }
            else {
                Entry entry = publishedEntry(new File(root(context), active), prefs(context).getString("latest." + active, null));
                directory = entry == null ? Config.dir(context) : entry.directory;
                available = entry != null;
            }
        }
        JSONObject overlay = null;
        if (!LEGACY.equals(active) && !available)
            return new Snapshot(Config.dir(context), null, active + "@" + currentRevision, currentRevision);
        if (!LEGACY.equals(active) && directory != null && directory.isDirectory()) try {
            JSONObject config = readJson(new File(directory, "config.json"));
            validateConfig(config, directory);
            overlay = config;
        } catch (Exception ignored) {
            return new Snapshot(Config.dir(context), null, active + "@" + currentRevision, currentRevision);
        }
        return new Snapshot(directory, overlay, active + "@" + currentRevision, currentRevision);
    }

    static JSONObject overlay(Context context) {
        return snapshot(context).overlay;
    }

    private static Entry publishedEntry(File idDir, String published) {
        if (published == null || published.isEmpty() || published.indexOf('/') >= 0 || published.indexOf('\\') >= 0) return null;
        File candidate = new File(idDir, published);
        try {
            ThemePackage.Manifest manifest = manifest(candidate);
            return manifest.id.equals(idDir.getName()) ? new Entry(manifest.id, manifest.name, manifest.version, candidate) : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    static Entry importZip(Context context, InputStream stream) throws IOException {
        File root = root(context);
        if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory()) throw new IOException("cannot create theme store");
        File staging = new File(root, ".incoming-" + UUID.randomUUID());
        if (!staging.mkdir()) throw new IOException("cannot create staging directory");
        File installed = null;
        boolean published = false;
        try {
            // ZIP reads and image decoding are intentionally outside LOCK: another caller can list/activate safely.
            ThemePackage.Manifest manifest = ThemePackage.extract(stream, staging);
            validateConfig(readJson(new File(staging, "config.json")), staging);
            synchronized (LOCK) {
                File idDir = new File(root, manifest.id);
                if (!idDir.isDirectory() && !idDir.mkdirs()) throw new IOException("cannot create theme directory");
                installed = new File(idDir, manifest.version + "-" + UUID.randomUUID());
                if (!staging.renameTo(installed)) throw new IOException("cannot install theme package");
                staging = null;
                Entry entry = new Entry(manifest.id, manifest.name, manifest.version, installed);
                SharedPreferences state = prefs(context);
                String latestKey = "latest." + manifest.id;
                String priorLatest = state.getString(latestKey, null);
                long priorRevision = state.getLong(REVISION, 0L);
                SharedPreferences.Editor stateEdit = state.edit().putString("latest." + manifest.id, installed.getName());
                if (manifest.id.equals(activeId(context))) stateEdit.putLong(REVISION, priorRevision + 1L);
                if (!stateEdit.commit()) {
                    SharedPreferences.Editor undo = state.edit().putLong(REVISION, priorRevision);
                    if (priorLatest == null) undo.remove(latestKey); else undo.putString(latestKey, priorLatest);
                    undo.commit();
                    throw new IOException("could not publish theme package");
                }
                published = true;
                return entry;
            }
        } finally {
            if (staging != null) deleteTree(staging);
            if (!published && installed != null) deleteTree(installed);
        }
    }

    static void activate(Context context, String id) throws IOException {
        synchronized (LOCK) {
            if (id == null) throw new IOException("theme id is required");
            boolean exists = LEGACY.equals(id);
            if (!exists) for (Entry entry : list(context)) if (id.equals(entry.id)) { exists = true; break; }
            if (!exists) throw new IOException("theme is not installed");
            SharedPreferences own = prefs(context);
            String priorId = own.getString(ACTIVE, LEGACY);
            long priorRevision = own.getLong(REVISION, 0L);
            if (!own.edit().putString(ACTIVE, id).putLong(REVISION, priorRevision + 1L).commit()) {
                own.edit().putString(ACTIVE, priorId).putLong(REVISION, priorRevision).commit();
                throw new IOException("could not save active theme");
            }
            SharedPreferences choices = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            long settingRevision = choices.getLong(Config.SETTINGS_REVISION_KEY, 0L) + 1L;
            if (!choices.edit().remove(Config.WALLPAPER_CHOICE_KEY)
                    .putLong(Config.SETTINGS_REVISION_KEY, settingRevision).commit()) {
                own.edit().putString(ACTIVE, priorId).putLong(REVISION, priorRevision).commit();
                throw new IOException("could not refresh launcher settings");
            }
        }
    }

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static File root(Context context) { return new File(context.getFilesDir(), ROOT); }

    private static ThemePackage.Manifest manifest(File dir) throws IOException {
        File file = new File(dir, "manifest.json");
        if (!file.isFile() || file.length() > ThemePackage.MAX_JSON) throw new IOException("missing manifest");
        {
            // Installed manifests were checked by ThemePackage before their immutable directory was published.
            JSONObject json = readJson(file);
            int schema = json.optInt("schema", -1);
            String id = json.optString("id", ""); String name = json.optString("name", ""); int version = json.optInt("version", 0);
            if (schema != 1 || LEGACY.equals(id) || !id.matches("[a-z0-9][a-z0-9-]{0,47}") || name.trim().isEmpty() || name.length() > 60 || version < 1)
                throw new IOException("invalid manifest");
            return new ThemePackage.Manifest(id, name, version);
        }
    }

    private static JSONObject readJson(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0 || file.length() > ThemePackage.MAX_JSON) throw new IOException("invalid JSON file");
        byte[] bytes = new byte[(int) file.length()]; int at = 0;
        try (InputStream in = new FileInputStream(file)) { for (int n; at < bytes.length && (n = in.read(bytes, at, bytes.length - at)) > 0;) at += n; }
        try { return new JSONObject(new String(bytes, 0, at, StandardCharsets.UTF_8)); }
        catch (JSONException e) { throw new IOException("invalid JSON", e); }
    }

    private static void validateConfig(JSONObject config, File directory) throws IOException {
        java.util.Iterator<String> keys = config.keys();
        while (keys.hasNext()) {
            String key = keys.next(); Object value = config.opt(key);
            if (!VISUAL.contains(key)) throw new IOException("theme contains non-visual setting: " + key);
            if (!validValue(key, value, directory)) throw new IOException("invalid theme setting: " + key);
        }
    }
    private static boolean validValue(String key, Object value, File directory) {
        if ("app_banners".equals(key)) {
            if (value instanceof JSONArray) {
                JSONArray values = (JSONArray) value;
                for (int i = 0; i < values.length(); i++) {
                    Object entry = values.opt(i);
                    if (!(entry instanceof String) || !validBannerEntry((String) entry, directory)) return false;
                }
                return true;
            }
            if (!(value instanceof JSONObject)) return false;
            JSONObject map = (JSONObject) value; java.util.Iterator<String> names = map.keys();
            while (names.hasNext()) { String app = names.next(); Object path = map.opt(app); if (!validPackage(app) || !(path instanceof String) || !validBanner((String) path, directory)) return false; }
            return true;
        }
        if ("wallpaper_coins".equals(key)) return validCoins(value, directory);
        if ("clock_font".equals(key)) return value instanceof String && validFont((String) value, directory);
        if ("clock_font_axes".equals(key)) return validAxes(value);
        if ("wallpaper_names".equals(key) || "wallpaper_tints".equals(key)) return value instanceof JSONArray || value instanceof JSONObject;
        if ("background_color".equals(key) || "header_style".equals(key) || "wallpaper_order".equals(key)) return value instanceof String;
        if ("wallpaper".equals(key) || "dock_glass".equals(key) || "dock_title".equals(key) || "dock_row".equals(key)
                || "dock_even_padding".equals(key) || "ambient_glow".equals(key) || "app_glow".equals(key)
                || "cinematic_home".equals(key) || "always_show_names".equals(key) || "reduced_motion".equals(key)
                || "wallpaper_true_color".equals(key) || "focus_overshoot".equals(key)) return value instanceof Boolean;
        return value instanceof Number;
    }
    private static boolean validBanner(String raw, File directory) {
        ThemeArtReference.Value ref = ThemeArtReference.parse(raw);
        if (ref == null || (ref.hasRegion() && (ref.x + ref.width > ThemeArtReference.MAX_SIDE
                || ref.y + ref.height > ThemeArtReference.MAX_SIDE))) return false;
        File asset = new File(directory, ref.path);
        try {
            if (!asset.isFile() || asset.length() <= 0 || asset.length() > ThemeArtReference.MAX_FILE_BYTES
                    || !asset.getCanonicalPath().startsWith(new File(directory, "banners").getCanonicalPath() + File.separator)) return false;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(asset.getPath(), options);
            return ThemeArtReference.safeBounds(options.outWidth, options.outHeight) && ref.fits(options.outWidth, options.outHeight);
        } catch (Exception e) { return false; }
    }

    /**
     * wallpaper_coins: wallpaper file name to an icon in the pack, {"01-cap.jpg": "icons/coin-cap.webp"} or
     * ["01-cap.jpg=icons/coin-cap.webp"]. Every value must name a real, small icon (validIcon); a picture without an
     * entry shows the vector coin, so an empty map is fine. The keys are not checked: a name no wallpaper has is unused.
     */
    private static boolean validCoins(Object value, File directory) {
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int i = 0; i < values.length(); i++) {
                Object entry = values.opt(i);
                if (!(entry instanceof String)) return false;
                String text = (String) entry;
                int equals = text.indexOf('=');
                if (equals < 0 || equals != text.lastIndexOf('=') || !validIcon(text.substring(equals + 1), directory)) return false;
            }
            return true;
        }
        if (!(value instanceof JSONObject)) return false;
        JSONObject map = (JSONObject) value;
        for (java.util.Iterator<String> names = map.keys(); names.hasNext();) {
            Object path = map.opt(names.next());
            if (!(path instanceof String) || !validIcon((String) path, directory)) return false;
        }
        return true;
    }

    /**
     * An icon HeaderStyle.coin accepts that is a file in the pack's icons/ folder, at most ThemePackage.MAX_ICON bytes
     * and at most MAX_ICON_SIDE px a side (a bounds-only decode, as for banners). OdysseyHeader.coin checks the same
     * again before it decodes, as the snapshot is read long after the import.
     */
    private static boolean validIcon(String path, File directory) {
        if (!HeaderStyle.coinPath(path)) return false;
        File icon = ThemePackage.inside(directory, "icons", path);
        if (icon == null || icon.length() <= 0 || icon.length() > ThemePackage.MAX_ICON) return false;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(icon.getPath(), options);
            return options.outWidth > 0 && options.outHeight > 0
                    && options.outWidth <= ThemePackage.MAX_ICON_SIDE && options.outHeight <= ThemePackage.MAX_ICON_SIDE;
        } catch (RuntimeException e) { return false; }
    }

    /**
     * clock_font: "fonts/<name>.ttf" (or .otf) naming a file in the pack's fonts/ folder, no larger than
     * ThemePackage.MAX_FONT, that starts like a single font (ThemeFont.sfnt). The ZIP reader already checked every
     * font's size and first bytes; this ties the setting to one of them.
     */
    private static boolean validFont(String path, File directory) {
        if (ThemeFont.path(path).isEmpty()) return false;
        File font = ThemePackage.inside(directory, ThemeFont.DIRECTORY, path);
        return font != null && font.length() <= ThemePackage.MAX_FONT && ThemeFont.sfnt(font);
    }

    /**
     * clock_font_axes: {"wght": 700, "wdth": 120} or ["wght=700", "wdth=120"]. In the object form every value must be
     * a JSON integer (not true, not 700.0); either way ThemeFont.axes decides the rest (tags, range, count, repeats).
     */
    private static boolean validAxes(Object value) {
        List<String> entries = new ArrayList<>();
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int i = 0; i < values.length(); i++) {
                Object entry = values.opt(i);
                if (!(entry instanceof String)) return false;
                entries.add((String) entry);
            }
        } else if (value instanceof JSONObject) {
            JSONObject map = (JSONObject) value;
            for (java.util.Iterator<String> tags = map.keys(); tags.hasNext();) {
                String tag = tags.next(); Object number = map.opt(tag);
                if (!(number instanceof Integer || number instanceof Long)) return false;
                entries.add(tag + "=" + number);
            }
        } else {
            return false;
        }
        return ThemeFont.axes(entries) != null;
    }

    private static boolean validBannerEntry(String entry, File directory) {
        int equals = entry.indexOf('=');
        return equals > 0 && equals == entry.lastIndexOf('=') && validPackage(entry.substring(0, equals).trim())
                && validBanner(entry.substring(equals + 1).trim(), directory);
    }
    private static boolean validPackage(String value) {
        return value != null && value.matches("(?:[A-Za-z][A-Za-z0-9_]*\\.)+[A-Za-z][A-Za-z0-9_]*");
    }
    private static void deleteTree(File file) {
        File[] kids = file.listFiles(); if (kids != null) for (File kid : kids) deleteTree(kid); file.delete();
    }
}
