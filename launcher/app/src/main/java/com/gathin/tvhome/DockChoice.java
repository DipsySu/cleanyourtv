package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The owner's own dock, chosen on the TV (hold OK on a tile) and kept in the launcher's private files (dock.txt).
 *
 * The dock is dock.txt when there is one, else a theme's dock_apps (config.json), else config.xml's dock_apps.
 * It is a list of packages, so it survives reboots, APK rebuilds, theme pushes, reinstalls and an app renaming its
 * launch activity; an app that is not installed has no tile until it comes back. Edits start from the dock as it is
 * shown (see {@link #of}): an entry whose app is gone at that moment is left out of the new list. Reset dock in the
 * Settings menu deletes the file, and only it, so the theme's dock_apps show again.
 *
 * Format, UTF-8 text: {@code tvhome-dock 1}, then one package per line, at most {@link #MAX}. No packages is a
 * valid choice (an empty dock that fill_dock fills). A file that does not decode is logged and ignored, not
 * deleted, so the theme's dock shows until the owner changes the dock again.
 */
final class DockChoice {

    private static final String TAG = "TvHome.Dock";
    static final String FILE_NAME = "dock.txt";
    private static final String MAGIC = "tvhome-dock 1";
    /** dock_columns tops out at 8 (Config), so no dock shows more. */
    static final int MAX = 8;
    /** The header and MAX packages of at most 255 characters each, with room to spare. */
    private static final int MAX_BYTES = 4096;

    private DockChoice() { }

    /** The dock Home shows: the owner's when there is one (even an empty one), else the theme's dock_apps. */
    static List<String> effective(List<String> owner, List<String> themeDock) {
        return owner != null ? owner : themeDock;
    }

    static String encode(List<String> dock) {
        if (!valid(dock)) throw new IllegalArgumentException("Invalid dock");
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        for (String pkg : dock) sb.append(pkg).append('\n');
        return sb.toString();
    }

    /** The dock in this text, or null when it is not one (wrong header, a bad or repeated package, too many). */
    static List<String> decode(String text) {
        if (text == null || text.length() > MAX_BYTES) return null;
        String[] lines = text.split("\n", -1);
        if (!lines[0].equals(MAGIC)) return null;
        List<String> dock = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String pkg = lines[i];
            if (pkg.isEmpty() && i == lines.length - 1) break;   // the trailing newline
            if (!LibraryApps.validPackage(pkg) || dock.contains(pkg)) return null;
            dock.add(pkg);
        }
        return dock.size() <= MAX ? Collections.unmodifiableList(dock) : null;
    }

    /** At most MAX packages, each a valid name and none twice. */
    static boolean valid(List<String> dock) {
        if (dock == null || dock.size() > MAX) return false;
        for (int i = 0; i < dock.size(); i++) {
            String pkg = dock.get(i);
            if (!LibraryApps.validPackage(pkg) || dock.indexOf(pkg) != i) return false;
        }
        return true;
    }

    /**
     * A dock list from packages as Home shows them (the dock tiles, in order): the first of each package, names
     * that are not packages left out, at most MAX.
     */
    static List<String> of(List<String> packages) {
        List<String> dock = new ArrayList<>();
        for (String pkg : packages) {
            if (dock.size() == MAX) break;
            if (LibraryApps.validPackage(pkg) && !dock.contains(pkg)) dock.add(pkg);
        }
        return Collections.unmodifiableList(dock);
    }

    // ---- sorting ---------------------------------------------------------------------------------

    /** Sort step for two tiles: dock apps first, in dock order; 0 falls through to the grid's own order. */
    static int compare(List<String> dock, String pkgA, String pkgB) {
        int a = dock.indexOf(pkgA), b = dock.indexOf(pkgB);
        if (a < 0 && b < 0) return 0;
        if (a < 0) return 1;
        if (b < 0) return -1;
        return Integer.compare(a, b);
    }

    /**
     * The packages the dock row shows for tiles sorted into this order: the leading dock apps and the empty slots
     * after them (fill_dock false, see {@link DockSlots}), or else the first dockSize tiles, grid apps filling it.
     */
    static List<String> dockRow(List<String> sortedPackages, List<String> dock, int dockSize, boolean fill) {
        int docked = 0;
        while (docked < sortedPackages.size() && dock.contains(sortedPackages.get(docked))) docked++;
        int shown = DockSlots.empty(docked, dockSize, sortedPackages.size(), fill) > 0 ? docked
                : Math.min(dockSize, sortedPackages.size());
        return of(sortedPackages.subList(0, shown));
    }

    /**
     * The dock to save for a dock row the owner arranged (Reorder, Edit dock, or an order.txt from before dock.txt):
     * the row, less the apps at its end that were neither in the dock before nor moved by the owner. With fill_dock
     * those were only filling free slots; saved after them in the tile order, they fill the same slots again.
     */
    static List<String> fromRow(List<String> row, Collection<String> keep) {
        int end = row.size();
        while (end > 0 && !keep.contains(row.get(end - 1))) end--;
        return of(row.subList(0, end));
    }

    /**
     * Once, for an order.txt saved before dock.txt existed and under today's dock_apps: the dock to save so Home keeps
     * the row it showed (oldRow), or null when the rows are the same and the theme's dock_apps can stay in charge.
     */
    static List<String> migrate(List<String> oldRow, List<String> newRow, List<String> themeDock) {
        return oldRow.equals(newRow) ? null : fromRow(oldRow, themeDock);
    }

    // ---- edits -----------------------------------------------------------------------------------

    /** True while the dock has a free place: fewer apps than the dock row's slots (and the file's limit). */
    static boolean hasRoom(List<String> dock, int slots) {
        return dock.size() < Math.min(slots, MAX);
    }

    /** Appends the app; unchanged when it is already in the dock or there is no room (then {@link #replace}). */
    static List<String> pin(List<String> dock, String pkg, int slots) {
        List<String> next = new ArrayList<>(dock);
        if (!dock.contains(pkg) && hasRoom(dock, slots)) next.add(pkg);
        return Collections.unmodifiableList(next);
    }

    /**
     * Puts the app in dock place slot; the app that was there leaves the dock. An app already elsewhere in the dock
     * swaps places with it instead. Unchanged for a place outside the dock.
     */
    static List<String> replace(List<String> dock, int slot, String pkg) {
        if (slot < 0 || slot >= dock.size()) return Collections.unmodifiableList(new ArrayList<>(dock));
        int at = dock.indexOf(pkg);
        if (at >= 0) return swap(dock, at, slot);
        List<String> next = new ArrayList<>(dock);
        next.set(slot, pkg);
        return Collections.unmodifiableList(next);
    }

    static List<String> unpin(List<String> dock, String pkg) {
        List<String> next = new ArrayList<>(dock);
        next.remove(pkg);
        return Collections.unmodifiableList(next);
    }

    /** Moves one app to another place; the ones between shift by one. Unchanged when either place is outside. */
    static List<String> move(List<String> dock, int from, int to) {
        List<String> next = new ArrayList<>(dock);
        if (from >= 0 && to >= 0 && from < dock.size() && to < dock.size()) next.add(to, next.remove(from));
        return Collections.unmodifiableList(next);
    }

    static List<String> swap(List<String> dock, int a, int b) {
        List<String> next = new ArrayList<>(dock);
        if (a >= 0 && b >= 0 && a < dock.size() && b < dock.size()) Collections.swap(next, a, b);
        return Collections.unmodifiableList(next);
    }

    /**
     * Where Move left or Move right takes the dock app at this place, or -1 when it would leave the dock apps
     * (the menu then leaves the row out). rtl_app_grid mirrors the row: place 0 is on the right, so Left goes up.
     */
    static int moveTarget(int position, boolean left, boolean rtl, int pinned) {
        if (position < 0 || position >= pinned) return -1;
        int to = left == rtl ? position + 1 : position - 1;
        return to >= 0 && to < pinned ? to : -1;
    }

    /**
     * Where an app taken out of the dock goes in the tile order (order.txt): right after the dock row, so the
     * free place is filled by the next grid app and not by the app just taken out, which would look as if nothing
     * happened. Without fill_dock the free place stays empty, so it goes right after the remaining dock apps,
     * unless none remain: then the first row fills (DockSlots) and it goes after that row too.
     */
    static int unpinnedPlace(int pinnedAfter, int dockSize, boolean fill) {
        return fill || pinnedAfter <= 0 ? dockSize : pinnedAfter;
    }

    /**
     * The tile order with these keys (an app's tiles, usually one) moved together to that place, counted without
     * them and clamped to the end. Keys that are not in the order are left out.
     */
    static List<String> placeKeys(List<String> keys, List<String> moving, int at) {
        List<String> next = new ArrayList<>(keys);
        List<String> moved = new ArrayList<>();
        for (String k : moving) if (next.remove(k)) moved.add(k);
        next.addAll(Math.max(0, Math.min(at, next.size())), moved);
        return Collections.unmodifiableList(next);
    }

    // ---- file ------------------------------------------------------------------------------------

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** The owner's dock, or null when there is none or it does not decode (then it is ignored, not deleted). */
    static List<String> load(File file) {
        if (!file.exists()) return null;
        if (file.length() > MAX_BYTES) {
            Log.w(TAG, file.getName() + " is too large; ignored");
            return null;
        }
        try {
            List<String> dock = decode(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            if (dock == null) Log.w(TAG, file.getName() + " is not valid; ignored");
            return dock;
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + file.getName() + ": " + e);
            return null;
        }
    }

    /** Writes the dock atomically. False (and a log line) when the list is not a dock or the write failed. */
    static boolean save(File file, List<String> dock) {
        byte[] bytes;
        try {
            bytes = encode(dock).getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "not saving an invalid dock: " + dock);
            return false;
        }
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(bytes);
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "cannot write " + file.getName() + ": " + e);
            if (out != null) af.failWrite(out);
            return false;
        }
    }

    /** Reset dock: deletes the owner's dock (and nothing else). False when it is still there. */
    static boolean reset(File file) {
        new AtomicFile(file).delete();
        return !file.exists();
    }
}
