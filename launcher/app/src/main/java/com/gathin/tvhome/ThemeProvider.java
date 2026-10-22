package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.gathin.tvhome.theme/config.json
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers      (every image in the collection)
 */
public class ThemeProvider extends ContentProvider {

    private static final String INCOMING = "incoming.zip";

    private void requireSecureWriter() throws FileNotFoundException {
        if (getContext().checkCallingPermission("android.permission.WRITE_SECURE_SETTINGS")
                != PackageManager.PERMISSION_GRANTED) throw new FileNotFoundException("WRITE_SECURE_SETTINGS required");
    }

    private File file(Uri uri) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        File dir = Config.dir(getContext());
        if (dir == null || path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(dir, path);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireSecureWriter();
        if (uri.getPathSegments().size() == 1 && INCOMING.equals(uri.getLastPathSegment())) {
            File inbox = new File(getContext().getFilesDir(), "theme-pack-inbox");
            if (!inbox.isDirectory() && !inbox.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
            File zip = new File(inbox, INCOMING);
            return ParcelFileDescriptor.open(zip, mode.contains("w")
                    ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                    : ParcelFileDescriptor.MODE_READ_ONLY);
        }
        File f = file(uri);
        if (mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try { requireSecureWriter(); } catch (FileNotFoundException denied) { return 0; }
        File dir = Config.dir(getContext());
        if (dir != null && ThemeFiles.isCollection(uri.getPathSegments())) {
            File[] images = new File(dir, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name));
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        }
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        try {
            requireSecureWriter();
            if ("listThemes".equals(method)) {
                org.json.JSONArray list = new org.json.JSONArray();
                String active = ThemeStore.activeId(getContext());
                for (ThemeStore.Entry entry : ThemeStore.list(getContext())) {
                    org.json.JSONObject item = new org.json.JSONObject();
                    item.put("id", entry.id); item.put("name", entry.name); item.put("version", entry.version);
                    item.put("active", entry.id.equals(active)); list.put(item);
                }
                out.putString("themes", list.toString()); out.putBoolean("ok", true);
            } else if ("importTheme".equals(method)) {
                File zip = new File(new File(getContext().getFilesDir(), "theme-pack-inbox"), INCOMING);
                ThemeStore.Entry entry = ThemeStore.importZip(getContext(), new java.io.ByteArrayInputStream(readStableIncoming(zip)));
                out.putBoolean("ok", true); out.putString("id", entry.id); out.putString("name", entry.name);
                notifyThemeChanged();
            } else if ("activateTheme".equals(method)) {
                ThemeStore.activate(getContext(), arg);
                out.putBoolean("ok", true); out.putString("id", arg);
                notifyThemeChanged();
            } else {
                out.putBoolean("ok", false); out.putString("error", "unknown theme operation");
            }
        } catch (Exception e) {
            out.putBoolean("ok", false); out.putString("error", concise(e));
        }
        return out;
    }

    private static String concise(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return "theme operation failed";
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private void notifyThemeChanged() {
        Intent changed = new Intent("com.gathin.tvhome.THEME_CHANGED");
        changed.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(changed);
    }

    /** Read an upload only after its observable file state stayed still: a client writes incoming.zip through a PFD. */
    private static byte[] readStableIncoming(File zip) throws java.io.IOException {
        long beforeLength = zip.length(), beforeModified = zip.lastModified();
        if (!zip.isFile() || beforeLength <= 0 || beforeLength > ThemePackage.MAX_COMPRESSED)
            throw new java.io.IOException("theme upload is missing or too large");
        byte[] bytes = new byte[(int) beforeLength]; int at = 0;
        try (java.io.InputStream in = new java.io.FileInputStream(zip)) {
            for (int n; at < bytes.length && (n = in.read(bytes, at, bytes.length - at)) > 0;) at += n;
        }
        if (at != bytes.length || zip.length() != beforeLength || zip.lastModified() != beforeModified)
            throw new java.io.IOException("theme upload changed while importing");
        return bytes;
    }
}
