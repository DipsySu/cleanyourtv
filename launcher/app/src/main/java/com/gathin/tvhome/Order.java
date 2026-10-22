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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The owner's own tile order, set with the reorder button and kept in the launcher's private files (order.txt).
 *
 * It orders the grid: dock apps (DockChoice) always come first, then saved tiles in this order; apps not in it
 * (new installs) follow by the usual rule (cards, then alphabetical) and uninstalled ones simply drop out. A save
 * leaves the dock apps out ({@link #gridKeys}), so a pushed theme or a rebuild with other dock_apps no longer has
 * to throw it away.
 *
 * Format, UTF-8 text: {@code tvhome-order 2}, then one tile key (component, flattened short) per line. Version 1
 * files, from before the owner dock, have a {@code dock <comma-separated dock_apps>} line after the header; they
 * are still read, apply only under those dock_apps as before ({@link #applies}), and MainActivity rewrites them as
 * version 2 once it has kept the dock they made (DockChoice).
 */
final class Order {

    private static final String TAG = "TvHome.Order";
    static final String FILE_NAME = "order.txt";
    private static final String MAGIC = "tvhome-order 2";
    private static final String MAGIC_V1 = "tvhome-order 1";
    private static final String DOCK = "dock ";

    /** Version 1 only: dock_apps the order was saved under, comma-joined (package names cannot contain commas). */
    final String legacyDock;
    final List<String> keys;

    Order(List<String> keys) {
        this(null, keys);
    }

    Order(String legacyDock, List<String> keys) {
        this.legacyDock = legacyDock;
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    }

    /** A version 1 dock line for these dock_apps, to tell whether an old order.txt still applied to them. */
    static String dockKey(List<String> dockApps) {
        return String.join(",", dockApps);
    }

    /** Always version 2: the dock line of a version 1 order is not written back. */
    static String encode(Order order) {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n');
        for (String k : order.keys) sb.append(k).append('\n');
        return sb.toString();
    }

    /** The order in this text, or null when it is not one (wrong header, a blank, malformed or duplicate key). */
    static Order decode(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        int first;
        String legacyDock = null;
        if (lines[0].equals(MAGIC)) {
            first = 1;
        } else if (lines[0].equals(MAGIC_V1) && lines.length >= 2 && lines[1].startsWith(DOCK)) {
            legacyDock = lines[1].substring(DOCK.length());
            first = 2;
        } else {
            return null;
        }
        List<String> keys = new ArrayList<>();
        for (int i = first; i < lines.length; i++) {
            String k = lines[i];
            if (k.isEmpty() && i == lines.length - 1) break;   // the trailing newline
            if (!validKey(k) || keys.contains(k)) return null;
            keys.add(k);
        }
        return new Order(legacyDock, keys);
    }

    /** A flattened component, package/class: never blank, never with spaces (so never a version 1 dock line). */
    static boolean validKey(String key) {
        if (key.indexOf('/') <= 0 || key.endsWith("/")) return false;
        for (int i = 0; i < key.length(); i++) if (Character.isWhitespace(key.charAt(i))) return false;
        return true;
    }

    /**
     * Whether this order ranks the tiles under these dock_apps: a version 2 order always; a version 1 order only
     * under the dock_apps it was saved with, as before. It ranked its dock row with the grid, so under other
     * dock_apps an app that left the dock would keep a dock place's rank and fill the place it left (see gridKeys).
     */
    static boolean applies(Order order, List<String> dockApps) {
        return order != null && (order.legacyDock == null || order.legacyDock.equals(dockKey(dockApps)));
    }

    /**
     * The keys to save for this tile order: all but the tiles of the dock's apps. Those sort first whatever their
     * rank, and the rank of a dock place would put an app that leaves the dock some other way than Remove from
     * dock (a theme's other dock_apps, Reset dock) first in the grid, where fill_dock shows it in a free dock
     * place as if it were still pinned. Left out, it joins the grid like a new install.
     */
    static List<String> gridKeys(List<String> keys, Collection<String> dock) {
        List<String> grid = new ArrayList<>();
        for (String k : keys) if (!dock.contains(packageOf(k))) grid.add(k);
        return grid;
    }

    /** The package of a tile key (package/class). */
    static String packageOf(String key) {
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : key;
    }

    /** Rank of each saved key (0 = first); an empty map without an order. Tiles without a rank sort after every ranked one. */
    static Map<String, Integer> ranks(Order order) {
        Map<String, Integer> r = new HashMap<>();
        if (order == null) return r;
        for (int i = 0; i < order.keys.size(); i++) r.put(order.keys.get(i), i);
        return r;
    }

    /** Sort step for two tiles: saved ones first in saved order, then 0 to fall through to the usual rule. */
    static int compare(Map<String, Integer> ranks, String keyA, String keyB) {
        Integer a = ranks.get(keyA), b = ranks.get(keyB);
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return Integer.compare(a, b);
    }

    // ---- file ------------------------------------------------------------------------------------

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** The saved order, or null when there is none or it does not decode (then it is ignored, not deleted). */
    static Order load(File file) {
        if (!file.exists()) return null;
        try {
            Order o = decode(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            if (o == null) Log.w(TAG, file.getName() + " is not valid; ignored");
            return o;
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + file.getName() + ": " + e);
            return null;
        }
    }

    /** Writes the order atomically. False (and a log line) when the write failed. */
    static boolean save(File file, Order order) {
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(encode(order).getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "cannot write " + file.getName() + ": " + e);
            if (out != null) af.failWrite(out);
            return false;
        }
    }
}
