package com.gathin.tvhome;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Safe, Android-free reader for a portable launcher theme package. */
final class ThemePackage {
    static final long MAX_COMPRESSED = 16L * 1024 * 1024;
    static final long MAX_EXTRACTED = 48L * 1024 * 1024;
    static final long MAX_FILE = 8L * 1024 * 1024;
    static final int MAX_ENTRIES = 128;
    static final int MAX_JSON = 64 * 1024;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,47}");
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*\\.(jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE);

    static final class Manifest {
        final String id, name;
        final int version;
        Manifest(String id, String name, int version) { this.id = id; this.name = name; this.version = version; }
    }

    private ThemePackage() {}

    /** Extracts only the documented theme files into an empty caller-owned staging directory. */
    static Manifest extract(InputStream source, File staging) throws IOException {
        if (source == null || staging == null || !staging.isDirectory() || (staging.list() != null && staging.list().length != 0))
            throw fail("invalid staging directory");
        File archive = new File(staging, ".incoming.zip");
        copyArchive(source, archive);
        Set<String> names = new HashSet<>();
        int entries = 0;
        long total = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                if (++entries > MAX_ENTRIES) throw fail("too many ZIP entries");
                String path = entry.getName();
                if (!allowed(path, entry.isDirectory()) || !names.add(path)) throw fail("unsafe ZIP entry");
                if (entry.isDirectory()) continue;
                long declared = entry.getSize();
                if (declared > MAX_FILE || (isJson(path) && declared > MAX_JSON)) throw fail("ZIP entry too large");
                File out = new File(staging, path);
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw fail("cannot create theme directory");
                long written = 0;
                byte[] buffer = new byte[8192];
                try (InputStream input = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        written += n; total += n;
                        if (written > MAX_FILE || total > MAX_EXTRACTED || (isJson(path) && written > MAX_JSON))
                            throw fail("theme payload too large");
                        fos.write(buffer, 0, n);
                    }
                }
            }
            File manifest = new File(staging, "manifest.json"), config = new File(staging, "config.json");
            if (!manifest.isFile() || manifest.length() == 0 || !config.isFile() || config.length() == 0)
                throw fail("manifest.json and config.json are required");
            return parseManifest(readSmall(manifest));
        } finally {
            archive.delete();
        }
    }

    private static void copyArchive(InputStream source, File archive) throws IOException {
        long total = 0;
        try (InputStream in = new BufferedInputStream(source); FileOutputStream out = new FileOutputStream(archive)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) {
                total += n;
                if (total > MAX_COMPRESSED) throw fail("ZIP stream too large");
                out.write(buffer, 0, n);
            }
        }
    }

    private static boolean allowed(String path, boolean directory) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0 || path.contains("..")) return false;
        if (directory) return "wallpapers/".equals(path) || "banners/".equals(path);
        if ("manifest.json".equals(path) || "config.json".equals(path) || "wallpaper.jpg".equals(path) || "preview.jpg".equals(path)) return true;
        int slash = path.indexOf('/');
        if (slash <= 0 || slash != path.lastIndexOf('/')) return false;
        String dir = path.substring(0, slash), leaf = path.substring(slash + 1);
        return ("wallpapers".equals(dir) || "banners".equals(dir)) && IMAGE.matcher(leaf).matches();
    }

    private static boolean isJson(String path) { return "manifest.json".equals(path) || "config.json".equals(path); }
    private static String readSmall(File file) throws IOException {
        if (file.length() > MAX_JSON) throw fail("JSON too large");
        byte[] bytes = new byte[(int) file.length()]; int at = 0;
        try (InputStream in = new FileInputStream(file)) {
            for (int n; at < bytes.length && (n = in.read(bytes, at, bytes.length - at)) > 0; ) at += n;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, at)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw fail("invalid UTF-8 JSON");
        }
    }

    private static Manifest parseManifest(String text) throws IOException {
        Map<String, Object> object = new Json(text).object();
        Object schema = object.get("schema"), id = object.get("id"), name = object.get("name"), version = object.get("version");
        if (!(schema instanceof Long) || ((Long) schema) != 1L || !(id instanceof String) || "legacy".equals(id) || !ID.matcher((String) id).matches()
                || !(name instanceof String) || !readable((String) name) || !(version instanceof Long)
                || ((Long) version) < 1 || ((Long) version) > Integer.MAX_VALUE) throw fail("invalid manifest");
        return new Manifest((String) id, (String) name, ((Long) version).intValue());
    }
    private static boolean readable(String value) {
        if (value.trim().isEmpty() || value.length() > 60) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }
    private static IOException fail(String message) { return new IOException(message); }

    /** Small strict JSON reader so ZIP validation remains usable in plain JVM tests. */
    private static final class Json {
        final String s; int p;
        Json(String s) { this.s = s == null ? "" : s; }
        int depth, keys;
        Map<String, Object> object() throws IOException { Object v = value(); ws(); if (p != s.length() || !(v instanceof Map)) throw fail("malformed manifest JSON"); return (Map<String, Object>) v; }
        Object value() throws IOException {
            if (++depth > 32) throw fail("JSON too deep");
            ws(); if (p >= s.length()) throw fail("malformed JSON"); char c = s.charAt(p);
            try {
                if (c == '{') return obj(); if (c == '[') return array(); if (c == '"') return string();
                if (c == '-' || Character.isDigit(c)) return number();
                if (take("true")) return Boolean.TRUE; if (take("false")) return Boolean.FALSE; if (take("null")) return null;
                throw fail("malformed JSON");
            } finally { depth--; }
        }
        Map<String, Object> obj() throws IOException { expect('{'); Map<String,Object> out = new HashMap<>(); ws(); if (eat('}')) return out; while (true) { if (++keys > 128) throw fail("JSON too complex"); ws(); if (p >= s.length() || s.charAt(p) != '"') throw fail("malformed JSON"); String k=string(); ws(); expect(':'); if (out.containsKey(k)) throw fail("duplicate JSON key"); out.put(k,value()); ws(); if (eat('}')) return out; expect(','); } }
        Object array() throws IOException { expect('['); ws(); if (eat(']')) return new Object[0]; int n=0; while (true) { value(); if (++n > 1024) throw fail("JSON too complex"); ws(); if (eat(']')) return new Object[0]; expect(','); } }
        String string() throws IOException { expect('"'); StringBuilder b=new StringBuilder(); while (p<s.length()) { char c=s.charAt(p++); if(c=='"') return b.toString(); if(c<' ') throw fail("bad string"); if(c=='\\') { if(p>=s.length()) throw fail("bad escape"); char e=s.charAt(p++); if(e=='u') { if(p+4>s.length()) throw fail("bad escape"); try { b.append((char)Integer.parseInt(s.substring(p,p+4),16)); } catch(NumberFormatException x){ throw fail("bad escape"); } p+=4; } else if("\"\\/bfnrt".indexOf(e)>=0) b.append(e=='b'?'\b':e=='f'?'\f':e=='n'?'\n':e=='r'?'\r':e=='t'?'\t':e); else throw fail("bad escape"); } else b.append(c); } throw fail("unterminated string"); }
        Long number() throws IOException { int start=p; if(s.charAt(p)=='-')p++; if(p>=s.length()||!Character.isDigit(s.charAt(p)))throw fail("bad number"); if(s.charAt(p)=='0' && p + 1 < s.length() && Character.isDigit(s.charAt(p + 1))) throw fail("bad number"); while(p<s.length()&&Character.isDigit(s.charAt(p)))p++; if(p<s.length() && ".eE".indexOf(s.charAt(p))>=0) throw fail("integer required"); try{return Long.parseLong(s.substring(start,p));}catch(NumberFormatException e){throw fail("bad number");} }
        void ws(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;} boolean eat(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;} void expect(char c)throws IOException{ws();if(!eat(c))throw fail("malformed JSON");} boolean take(String v){if(s.startsWith(v,p)){p+=v.length();return true;}return false;}
    }
}
