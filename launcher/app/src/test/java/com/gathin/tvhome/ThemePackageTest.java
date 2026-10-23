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
import static org.junit.Assert.assertNull;
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

    @Test public void extractsFontsAndIconsWithTheirFolders() throws Exception {
        Map<String, byte[]> files = basic();
        files.put("fonts/", new byte[0]);
        files.put("icons/", new byte[0]);
        files.put("fonts/fredoka.ttf", font(0, 1, 0, 0));
        files.put("fonts/Digits.OTF", font('O', 'T', 'T', 'O'));
        files.put("fonts/apple.ttf", font('t', 'r', 'u', 'e'));
        files.put("icons/coin-cap.webp", new byte[] {1, 2, 3});
        files.put("icons/Coin_Moon.PNG", new byte[] {4});
        File dir = Files.createTempDirectory("theme-package-fonts").toFile();
        try {
            ThemePackage.extract(new ByteArrayInputStream(zip(files)), dir);
            assertTrue(new File(dir, "fonts/fredoka.ttf").isFile());
            assertTrue(new File(dir, "fonts/Digits.OTF").isFile());
            assertTrue(new File(dir, "fonts/apple.ttf").isFile());
            assertTrue(new File(dir, "icons/coin-cap.webp").isFile());
            assertTrue(new File(dir, "icons/Coin_Moon.PNG").isFile());
        } finally {
            delete(dir);
        }
    }

    @Test public void refusesFontsThatDoNotStartLikeOneFont() throws Exception {
        assertRejectFont(font('t', 't', 'c', 'f'));   // a collection
        assertRejectFont(font('w', 'O', 'F', 'F'));
        assertRejectFont(font('w', 'O', 'F', '2'));
        assertRejectFont("not a font at all".getBytes(StandardCharsets.UTF_8));
        assertRejectFont(new byte[] {0, 1, 0});
        assertRejectFont(new byte[0]);
    }

    @Test public void refusesAFontBeyondItsOwnLimit() throws Exception {
        byte[] large = new byte[(int) ThemePackage.MAX_FONT + 1];
        large[1] = 1;   // 00 01 00 00: a TrueType tag, so only the size is wrong
        assertRejectFont(large);
        byte[] limit = java.util.Arrays.copyOf(large, (int) ThemePackage.MAX_FONT);
        Map<String, byte[]> files = basic(); files.put("fonts/limit.ttf", limit);
        File dir = Files.createTempDirectory("theme-package-font-limit").toFile();
        try {
            ThemePackage.extract(new ByteArrayInputStream(zip(files)), dir);
            assertEquals(ThemePackage.MAX_FONT, new File(dir, "fonts/limit.ttf").length());
        } finally {
            delete(dir);
        }
    }

    @Test public void refusesFilesInTheWrongFolder() throws Exception {
        assertReject(with("fonts/clock.woff2", font(0, 1, 0, 0)));
        assertReject(with("fonts/clock.ttc", font(0, 1, 0, 0)));
        assertReject(with("fonts/coin.png", new byte[] {1}));
        assertReject(with("icons/clock.ttf", font(0, 1, 0, 0)));
        assertReject(with("banners/clock.ttf", font(0, 1, 0, 0)));
        assertReject(with("clock.ttf", font(0, 1, 0, 0)));
        assertReject(with("fonts/nested/clock.ttf", font(0, 1, 0, 0)));
        assertReject(with("icons/nested/coin.png", new byte[] {1}));
        assertReject(with("Fonts/clock.ttf", font(0, 1, 0, 0)));
        assertReject(with("icons/.coin.png", new byte[] {1}));
        assertReject(with("fonts/nested/", new byte[0]));
        assertReject(with("glyphs/", new byte[0]));
    }

    @Test public void insideResolvesOnlyRealFilesUnderTheFolder() throws Exception {
        File dir = Files.createTempDirectory("theme-package-inside").toFile();
        try {
            File icons = new File(dir, "icons"); icons.mkdirs();
            File coin = new File(icons, "coin.webp"); Files.write(coin.toPath(), new byte[] {1});
            File secret = new File(dir, "config.json"); Files.write(secret.toPath(), new byte[] {'{', '}'});
            assertEquals(coin.getCanonicalFile(), ThemePackage.inside(dir, "icons", "icons/coin.webp"));
            assertNull(ThemePackage.inside(dir, "icons", "icons/../config.json"));
            assertNull(ThemePackage.inside(dir, "fonts", "icons/coin.webp"));
            assertNull(ThemePackage.inside(dir, "icons", "icons/missing.webp"));
            assertNull(ThemePackage.inside(dir, "icons", "icons"));
            assertNull(ThemePackage.inside(dir, "icons", "icons/"));
            assertNull(ThemePackage.inside(null, "icons", "icons/coin.webp"));
            assertNull(ThemePackage.inside(dir, "icons", null));
            // A link that leads out of the folder is refused even though its own name looks right.
            File link = new File(icons, "link.webp");
            Files.createSymbolicLink(link.toPath(), secret.toPath());
            assertNull(ThemePackage.inside(dir, "icons", "icons/link.webp"));
        } finally {
            delete(dir);
        }
    }

    private static Map<String, byte[]> with(String name, byte[] bytes) { Map<String, byte[]> files = basic(); files.put(name, bytes); return files; }
    private static void assertRejectFont(byte[] bytes) throws Exception { assertReject(with("fonts/clock.ttf", bytes)); }
    /** Four tag bytes, then a little more, as a font's table directory follows its tag. */
    private static byte[] font(int a, int b, int c, int d) { return new byte[] {(byte) a, (byte) b, (byte) c, (byte) d, 0, 9, 0, -128}; }
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
