package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeFontTest {
    @Test public void fontNamesAreTrueTypeOrOpenTypeFilesOnly() {
        assertTrue(ThemeFont.leaf("fredoka.ttf"));
        assertTrue(ThemeFont.leaf("Clock-Digits_2.OTF"));
        assertTrue(ThemeFont.leaf("a.TtF"));
        assertFalse(ThemeFont.leaf("fredoka.ttc"));
        assertFalse(ThemeFont.leaf("fredoka.woff2"));
        assertFalse(ThemeFont.leaf("fredoka.xml"));
        assertFalse(ThemeFont.leaf(".fredoka.ttf"));
        assertFalse(ThemeFont.leaf("fredoka.ttf.png"));
        assertFalse(ThemeFont.leaf("fre doka.ttf"));
        assertFalse(ThemeFont.leaf("sub/fredoka.ttf"));
        assertFalse(ThemeFont.leaf(".ttf"));
        assertFalse(ThemeFont.leaf(null));
    }

    @Test public void aFontPathIsAFileDirectlyInFonts() {
        assertEquals("fonts/fredoka.ttf", ThemeFont.path("fonts/fredoka.ttf"));
        assertEquals("fonts/Digits.OTF", ThemeFont.path("fonts/Digits.OTF"));
        assertEquals("", ThemeFont.path("fonts/../config.json"));
        assertEquals("", ThemeFont.path("fonts/a..ttf"));   // '..' anywhere, as the ZIP rules refuse it
        assertEquals("", ThemeFont.path("fonts/nested/fredoka.ttf"));
        assertEquals("", ThemeFont.path("fonts/fredoka.ttf#1"));
        assertEquals("", ThemeFont.path("Fonts/fredoka.ttf"));
        assertEquals("", ThemeFont.path("/fonts/fredoka.ttf"));
        assertEquals("", ThemeFont.path("fonts\\fredoka.ttf"));
        assertEquals("", ThemeFont.path("banners/fredoka.ttf"));
        assertEquals("", ThemeFont.path("fredoka.ttf"));
        assertEquals("", ThemeFont.path("fonts/"));
        assertEquals("", ThemeFont.path("fonts/fredoka.webp"));
        assertEquals("", ThemeFont.path(" fonts/fredoka.ttf"));
        assertEquals("", ThemeFont.path(""));
        assertEquals("", ThemeFont.path(null));
    }

    @Test public void onlySingleFontTagsPass() {
        assertTrue(ThemeFont.sfnt(new byte[] {0, 1, 0, 0, 0, 16}, 6));
        assertTrue(ThemeFont.sfnt(ascii("OTTO"), 4));
        assertTrue(ThemeFont.sfnt(ascii("true"), 4));
        assertFalse(ThemeFont.sfnt(ascii("ttcf"), 4));   // a collection
        assertFalse(ThemeFont.sfnt(ascii("wOFF"), 4));
        assertFalse(ThemeFont.sfnt(ascii("wOF2"), 4));
        assertFalse(ThemeFont.sfnt(ascii("otto"), 4));
        assertFalse(ThemeFont.sfnt(ascii("PK\u0003\u0004"), 4));
        assertFalse(ThemeFont.sfnt(new byte[] {0, 1, 0, 1}, 4));
        // Fewer than four bytes read is no font, whatever the buffer holds.
        assertFalse(ThemeFont.sfnt(new byte[] {0, 1, 0, 0}, 3));
        assertFalse(ThemeFont.sfnt(new byte[] {0, 1, 0}, 3));
        assertFalse(ThemeFont.sfnt(null, 4));
    }

    @Test public void aFileIsCheckedByItsFirstBytes() throws Exception {
        File dir = Files.createTempDirectory("theme-font-test").toFile();
        try {
            File font = write(new File(dir, "a.ttf"), new byte[] {0, 1, 0, 0, 0, 9, 0, -128});
            File cff = write(new File(dir, "b.otf"), ascii("OTTO and the rest"));
            File woff = write(new File(dir, "c.ttf"), ascii("wOFF and the rest"));
            File shortFile = write(new File(dir, "d.ttf"), new byte[] {0, 1, 0});
            File empty = write(new File(dir, "e.ttf"), new byte[0]);
            assertTrue(ThemeFont.sfnt(font));
            assertTrue(ThemeFont.sfnt(cff));
            assertFalse(ThemeFont.sfnt(woff));
            assertFalse(ThemeFont.sfnt(shortFile));
            assertFalse(ThemeFont.sfnt(empty));
            assertFalse(ThemeFont.sfnt(new File(dir, "missing.ttf")));
            assertFalse(ThemeFont.sfnt(dir));
            assertFalse(ThemeFont.sfnt((File) null));
        } finally {
            delete(dir);
        }
    }

    @Test public void axesBecomeTheVariationStringInTheirOrder() {
        assertEquals("'wght' 700, 'wdth' 120", ThemeFont.axes(Arrays.asList("wght=700", "wdth=120")));
        assertEquals("'wdth' 120, 'wght' 700", ThemeFont.axes(Arrays.asList("wdth=120", "wght=700")));
        assertEquals("'slnt' -10", ThemeFont.axes(Collections.singletonList("slnt=-10")));
        assertEquals("'GRAD' 0", ThemeFont.axes(Collections.singletonList("GRAD=-0")));
        assertEquals("'wght' 700", ThemeFont.axes(Collections.singletonList("wght=0700")));
        assertEquals("'opsz' -10000, 'XTRA' 10000", ThemeFont.axes(Arrays.asList("opsz=-10000", "XTRA=10000")));
    }

    @Test public void noAxesIsTheDefaultInstance() {
        assertEquals("", ThemeFont.axes(Collections.emptyList()));
        assertEquals("", ThemeFont.axes(null));
    }

    @Test public void anyWrongAxisRejectsThemAll() {
        assertNull(ThemeFont.axes(Arrays.asList("wght=700", "wdth=12.5")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=7e2")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=+700")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght= 700")));
        assertNull(ThemeFont.axes(Collections.singletonList(" wght=700")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=true")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=7=0")));
        assertNull(ThemeFont.axes(Collections.singletonList("wgh=700")));
        assertNull(ThemeFont.axes(Collections.singletonList("wghts=700")));
        assertNull(ThemeFont.axes(Collections.singletonList("wgh1=700")));
        assertNull(ThemeFont.axes(Collections.singletonList("wgh'=700")));
        assertNull(ThemeFont.axes(Collections.singletonList("=700")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=10001")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=-10001")));
        assertNull(ThemeFont.axes(Collections.singletonList("wght=99999999999999999999999")));
        assertNull(ThemeFont.axes(Arrays.asList("wght=700", "wght=400")));   // a tag twice
        assertNull(ThemeFont.axes(Arrays.asList("wght=700", null)));
    }

    @Test public void atMostEightAxes() {
        List<String> eight = new ArrayList<>();
        for (String tag : new String[] {"aaaa", "bbbb", "cccc", "dddd", "eeee", "ffff", "gggg", "hhhh"}) eight.add(tag + "=1");
        assertEquals(8, ThemeFont.axes(eight).split(", ").length);
        eight.add("iiii=1");
        assertNull(ThemeFont.axes(eight));
    }

    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.ISO_8859_1); }
    private static File write(File file, byte[] bytes) throws Exception { Files.write(file.toPath(), bytes); return file; }
    private static void delete(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) delete(child); file.delete(); }
}
