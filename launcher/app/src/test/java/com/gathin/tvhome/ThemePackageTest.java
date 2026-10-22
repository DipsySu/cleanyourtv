package com.gathin.tvhome;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemePackageTest {
    @Test public void extractsStrictPortablePackage() throws Exception {
        Map<String, byte[]> files = basic();
        files.put("wallpapers/sunset.webp", new byte[] {1, 2, 3});
        files.put("banners/atlas.png", new byte[] {4, 5});
        File dir = Files.createTempDirectory("theme-package-test").toFile();
        ThemePackage.Manifest manifest = ThemePackage.extract(new ByteArrayInputStream(zip(files)), dir);
        assertEquals("animal-crossing", manifest.id);
        assertEquals("岛屿黄昏", manifest.name);
        assertEquals(2, manifest.version);
        assertTrue(new File(dir, "banners/atlas.png").isFile());
        delete(dir);
    }

    @Test public void refusesTraversalAndUnknownExecutable() throws Exception {
        assertReject(map("manifest.json", manifest(), "config.json", "{}", "../escape", "no"));
        assertReject(map("manifest.json", manifest(), "config.json", "{}", "banners/run.sh", "no"));
        assertReject(map("manifest.json", manifest(), "config.json", "{}", "wallpapers/nested/a.jpg", "no"));
    }

    @Test public void refusesMalformedManifest() throws Exception {
        assertReject(map("manifest.json", "{\"schema\":1,\"id\":\"BAD\",\"name\":\"ok\",\"version\":1}", "config.json", "{}"));
        assertReject(map("manifest.json", "{\"schema\":1,\"id\":\"legacy\",\"name\":\"ok\",\"version\":1}", "config.json", "{}"));
        assertReject(map("manifest.json", "{\"schema\":1,\"id\":\"ok\",\"name\":\"ok\",\"version\":1", "config.json", "{}"));
    }

    @Test public void refusesPayloadBeyondPerFileLimit() throws Exception {
        byte[] huge = new byte[(int) ThemePackage.MAX_FILE + 1];
        Map<String, byte[]> files = basic(); files.put("wallpapers/huge.jpg", huge);
        assertReject(zip(files));
    }

    @Test public void refusesTruncatedCentralDirectory() throws Exception {
        byte[] complete = zip(basic());
        byte[] truncated = java.util.Arrays.copyOf(complete, complete.length - 8);
        assertReject(truncated);
    }

    private static Map<String, byte[]> basic() { return map("manifest.json", manifest(), "config.json", "{\"header_style\":\"animal_crossing\"}"); }
    private static String manifest() { return "{\"schema\":1,\"id\":\"animal-crossing\",\"name\":\"岛屿黄昏\",\"version\":2}"; }
    private static Map<String, byte[]> map(String... pairs) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1].getBytes(StandardCharsets.UTF_8));
        return out;
    }
    private static byte[] zip(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) add(out, entry.getKey(), entry.getValue());
        }
        return bytes.toByteArray();
    }
    private static void add(ZipOutputStream out, String name, String value) throws Exception { add(out, name, value.getBytes(StandardCharsets.UTF_8)); }
    private static void add(ZipOutputStream out, String name, byte[] value) throws Exception { out.putNextEntry(new ZipEntry(name)); out.write(value); out.closeEntry(); }
    private static void assertReject(Map<String, byte[]> files) throws Exception { assertReject(zip(files)); }
    private static void assertReject(byte[] zip) throws Exception {
        File dir = Files.createTempDirectory("theme-package-reject").toFile();
        try { ThemePackage.extract(new ByteArrayInputStream(zip), dir); fail("expected rejected ZIP"); }
        catch (java.io.IOException expected) { assertFalse(new File(dir, "escape").exists()); }
        finally { delete(dir); }
    }
    private static void delete(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) delete(child); file.delete(); }
}
