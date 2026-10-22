package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Plain JVM tests for the owner's dock (dock.txt): file format, the list edits, and how the tiles come out sorted
 * the way AppAdapter sorts them (dock apps first, then the saved order, then alphabetical).
 */
public class DockChoiceTest {

    private static final int SLOTS = 6;
    private static final List<String> THEME = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.f");

    // ---- file format -----------------------------------------------------------------------------

    @Test
    public void roundTrips() {
        List<String> dock = Arrays.asList("com.netflix.ninja", "com.plexapp.android", "tv.emby.embyatv");
        assertEquals("tvhome-dock 1\ncom.netflix.ninja\ncom.plexapp.android\ntv.emby.embyatv\n", DockChoice.encode(dock));
        assertEquals(dock, DockChoice.decode(DockChoice.encode(dock)));
    }

    @Test
    public void anEmptyDockIsAChoiceToo() {
        assertEquals(Collections.emptyList(), DockChoice.decode(DockChoice.encode(Collections.emptyList())));
        assertEquals(Collections.emptyList(), DockChoice.decode("tvhome-dock 1"));
        assertEquals(Collections.emptyList(), DockChoice.effective(Collections.emptyList(), THEME));
    }

    @Test
    public void readsAFileWithoutTheLastNewline() {
        assertEquals(Arrays.asList("com.a", "com.b"), DockChoice.decode("tvhome-dock 1\ncom.a\ncom.b"));
    }

    @Test
    public void holdsUpToEightApps() {
        List<String> eight = Arrays.asList("a.a", "a.b", "a.c", "a.d", "a.e", "a.f", "a.g", "a.h");
        assertEquals(eight, DockChoice.decode(DockChoice.encode(eight)));
        assertNull(DockChoice.decode(DockChoice.encode(eight) + "a.i\n"));
    }

    @Test
    public void rejectsWhatIsNotADock() {
        assertNull(DockChoice.decode(null));
        assertNull(DockChoice.decode(""));
        assertNull(DockChoice.decode("com.a\ncom.b\n"));                        // no header
        assertNull(DockChoice.decode("tvhome-dock 2\ncom.a\n"));
        assertNull(DockChoice.decode("tvhome-order 2\ncom.a/.Main\n"));
        assertNull(DockChoice.decode("tvhome-dock 1\ncom.a\ncom.a\n"));         // twice
        assertNull(DockChoice.decode("tvhome-dock 1\ncom.a\n\ncom.b\n"));       // blank line in the middle
        assertNull(DockChoice.decode("tvhome-dock 1\ncom.a\r\n"));              // CRLF
        assertNull(DockChoice.decode("tvhome-dock 1\ncom.a/.Main\n"));          // a tile key, not a package
        for (String bad : new String[]{"com..a", ".com.a", "com.a.", "1com.a", "com.1a", "com a", "com-a.b"}) {
            assertNull(bad, DockChoice.decode("tvhome-dock 1\n" + bad + "\n"));
        }
        StringBuilder huge = new StringBuilder("tvhome-dock 1\n");
        while (huge.length() <= 4096) huge.append('a');
        assertNull(DockChoice.decode(huge.toString()));
    }

    @Test
    public void neverWritesWhatItWouldNotRead() {
        assertThrows(IllegalArgumentException.class, () -> DockChoice.encode(null));
        assertThrows(IllegalArgumentException.class, () -> DockChoice.encode(Arrays.asList("com.a", "com.a")));
        assertThrows(IllegalArgumentException.class, () -> DockChoice.encode(Arrays.asList("com.a", "not a package")));
        assertThrows(IllegalArgumentException.class, () -> DockChoice.encode(
                Arrays.asList("a.a", "a.b", "a.c", "a.d", "a.e", "a.f", "a.g", "a.h", "a.i")));
        assertFalse(DockChoice.valid(Collections.singletonList("")));
        assertTrue(DockChoice.valid(Collections.emptyList()));
    }

    // ---- which dock ------------------------------------------------------------------------------

    @Test
    public void theOwnersDockWinsOverTheThemes() {
        List<String> owner = Arrays.asList("com.z", "com.a");
        assertSame(owner, DockChoice.effective(owner, THEME));
        assertSame(THEME, DockChoice.effective(null, THEME));   // no dock.txt, or one that does not decode
    }

    @Test
    public void ofKeepsTheFirstOfEachPackageAndLeavesOutNonPackages() {
        assertEquals(Arrays.asList("com.a", "com.b"), DockChoice.of(Arrays.asList("com.a", "com.b", "com.a", "bad name")));
        List<String> many = new ArrayList<>();
        for (char c = 'a'; c <= 'k'; c++) many.add("com." + c);
        assertEquals(DockChoice.MAX, DockChoice.of(many).size());
        assertEquals(many.subList(0, DockChoice.MAX), DockChoice.of(many));
    }

    @Test
    public void dockAppsSortFirstInDockOrder() {
        List<String> dock = Arrays.asList("com.b", "com.a");
        assertTrue(DockChoice.compare(dock, "com.b", "com.a") < 0);
        assertTrue(DockChoice.compare(dock, "com.a", "com.zzz") < 0);
        assertTrue(DockChoice.compare(dock, "com.zzz", "com.b") > 0);
        assertEquals(0, DockChoice.compare(dock, "com.y", "com.x"));   // both grid apps: the grid's own order decides
    }

    // ---- edits -----------------------------------------------------------------------------------

    @Test
    public void pinAppendsUntilEveryPlaceIsTaken() {
        List<String> five = THEME.subList(0, 5);
        assertTrue(DockChoice.hasRoom(five, SLOTS));
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.z"), DockChoice.pin(five, "com.z", SLOTS));
        assertFalse(DockChoice.hasRoom(THEME, SLOTS));
        assertEquals(THEME, DockChoice.pin(THEME, "com.z", SLOTS));    // full: the owner picks a place (replace)
        assertEquals(five, DockChoice.pin(five, "com.c", SLOTS));      // already in the dock
        assertEquals(Collections.singletonList("com.z"), DockChoice.pin(Collections.emptyList(), "com.z", SLOTS));
        List<String> eight = Arrays.asList("a.a", "a.b", "a.c", "a.d", "a.e", "a.f", "a.g", "a.h");
        assertFalse(DockChoice.hasRoom(eight, 10));                     // dock_columns never exceeds the file's limit
    }

    @Test
    public void replacePutsTheAppInThePlaceChosen() {
        assertEquals(Arrays.asList("com.a", "com.b", "com.z", "com.d", "com.e", "com.f"), DockChoice.replace(THEME, 2, "com.z"));
        assertEquals(Arrays.asList("com.z", "com.b", "com.c", "com.d", "com.e", "com.f"), DockChoice.replace(THEME, 0, "com.z"));
        // An app already in the dock swaps places, so nothing is lost or doubled.
        assertEquals(Arrays.asList("com.e", "com.b", "com.c", "com.d", "com.a", "com.f"), DockChoice.replace(THEME, 0, "com.e"));
        assertEquals(THEME, DockChoice.replace(THEME, 6, "com.z"));
        assertEquals(THEME, DockChoice.replace(THEME, -1, "com.z"));
    }

    @Test
    public void unpinMoveAndSwap() {
        assertEquals(Arrays.asList("com.a", "com.c", "com.d", "com.e", "com.f"), DockChoice.unpin(THEME, "com.b"));
        assertEquals(THEME, DockChoice.unpin(THEME, "com.z"));
        assertEquals(Arrays.asList("com.b", "com.a", "com.c", "com.d", "com.e", "com.f"), DockChoice.move(THEME, 0, 1));
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.f", "com.e"), DockChoice.move(THEME, 5, 4));
        assertEquals(Arrays.asList("com.b", "com.c", "com.a", "com.d", "com.e", "com.f"), DockChoice.move(THEME, 0, 2));
        assertEquals(THEME, DockChoice.move(THEME, 5, 6));
        assertEquals(Arrays.asList("com.f", "com.b", "com.c", "com.d", "com.e", "com.a"), DockChoice.swap(THEME, 0, 5));
        assertEquals(THEME, DockChoice.swap(THEME, 0, 9));
        assertTrue(DockChoice.valid(DockChoice.move(THEME, 1, 4)));
    }

    @Test
    public void movesStayAmongTheDockApps() {
        // Left to right: place 0 is on the left.
        assertEquals(-1, DockChoice.moveTarget(0, true, false, 6));
        assertEquals(1, DockChoice.moveTarget(0, false, false, 6));
        assertEquals(4, DockChoice.moveTarget(5, true, false, 6));
        assertEquals(-1, DockChoice.moveTarget(5, false, false, 6));
        assertEquals(-1, DockChoice.moveTarget(3, false, false, 4));   // the last dock app before grid apps filling the rest
        // rtl_app_grid: place 0 is on the right, so Left goes towards the end.
        assertEquals(1, DockChoice.moveTarget(0, true, true, 6));
        assertEquals(-1, DockChoice.moveTarget(0, false, true, 6));
        assertEquals(-1, DockChoice.moveTarget(5, true, true, 6));
        assertEquals(4, DockChoice.moveTarget(5, false, true, 6));
        // One dock app has nowhere to go; a grid position is not a dock app.
        assertEquals(-1, DockChoice.moveTarget(0, true, false, 1));
        assertEquals(-1, DockChoice.moveTarget(0, false, false, 1));
        assertEquals(-1, DockChoice.moveTarget(6, true, false, 6));
        assertEquals(-1, DockChoice.moveTarget(-1, false, false, 6));
    }

    @Test
    public void placeKeysMovesAnAppsTilesTogether() {
        List<String> keys = Arrays.asList("a/.A", "b/.B", "c/.C", "d/.D");
        assertEquals(Arrays.asList("b/.B", "c/.C", "a/.A", "d/.D"), DockChoice.placeKeys(keys, Collections.singletonList("a/.A"), 2));
        assertEquals(Arrays.asList("c/.C", "d/.D", "a/.A", "b/.B"), DockChoice.placeKeys(keys, Arrays.asList("a/.A", "b/.B"), 9));
        assertEquals(keys, DockChoice.placeKeys(keys, Collections.singletonList("gone/.Gone"), 0));
    }

    // ---- taking an app out of the dock -----------------------------------------------------------

    @Test
    public void anUnpinnedAppGoesAfterTheDockRow() {
        assertEquals(6, DockChoice.unpinnedPlace(5, 6, true));    // fill_dock: after the grid apps filling the dock
        assertEquals(6, DockChoice.unpinnedPlace(0, 6, true));
        assertEquals(2, DockChoice.unpinnedPlace(2, 6, false));   // empty places stay empty: right after the dock apps
        assertEquals(6, DockChoice.unpinnedPlace(0, 6, false));   // no dock app left: the first row fills (DockSlots)
    }

    @Test
    public void anAppTakenOutOfAFullDockDoesNotFillItsOwnPlace() {
        // com.b sorts first of the grid apps, so without the rule it would fill the free place itself and the
        // dock would look as if Remove from dock had done nothing.
        List<String> tiles = Arrays.asList("com.c", "com.d", "com.e", "com.f", "com.g", "com.b", "com.h", "com.i");
        List<String> dock = Arrays.asList("com.b", "com.c", "com.d", "com.e", "com.f", "com.g");
        List<String> shown = sorted(tiles, dock, Collections.emptyMap());
        assertEquals(dock, DockChoice.dockRow(shown, dock, SLOTS, true));

        List<String> next = DockChoice.unpin(dock, "com.b");
        assertTrue(DockChoice.dockRow(sorted(tiles, next, Collections.emptyMap()), next, SLOTS, true).contains("com.b"));

        List<String> order = Order.gridKeys(DockChoice.placeKeys(keys(shown), Collections.singletonList(key("com.b")),
                DockChoice.unpinnedPlace(next.size(), SLOTS, true)), next);
        assertEquals(keys(Arrays.asList("com.h", "com.b", "com.i")), order);
        List<String> after = sorted(tiles, next, Order.ranks(new Order(order)));
        assertEquals(Arrays.asList("com.c", "com.d", "com.e", "com.f", "com.g", "com.h"),
                DockChoice.dockRow(after, next, SLOTS, true));
        assertEquals(SLOTS, after.indexOf("com.b"));   // first in the grid, right under the dock
    }

    @Test
    public void anAppTakenOutWhileGridAppsFillTheDockGoesAfterThem() {
        // Four dock apps; com.a and com.h fill places 4 and 5. Taking com.d out: com.i fills the new free place.
        List<String> tiles = Arrays.asList("com.a", "com.c", "com.d", "com.e", "com.f", "com.h", "com.i", "com.j");
        List<String> dock = Arrays.asList("com.c", "com.d", "com.e", "com.f");
        List<String> shown = sorted(tiles, dock, Collections.emptyMap());
        assertEquals(Arrays.asList("com.c", "com.d", "com.e", "com.f", "com.a", "com.h"), shown.subList(0, SLOTS));

        List<String> next = DockChoice.unpin(dock, "com.d");
        List<String> order = Order.gridKeys(DockChoice.placeKeys(keys(shown), Collections.singletonList(key("com.d")),
                DockChoice.unpinnedPlace(next.size(), SLOTS, true)), next);
        List<String> after = sorted(tiles, next, Order.ranks(new Order(order)));
        assertEquals(Arrays.asList("com.c", "com.e", "com.f", "com.a", "com.h", "com.i"), DockChoice.dockRow(after, next, SLOTS, true));
        assertEquals(SLOTS, after.indexOf("com.d"));
    }

    @Test
    public void withoutFillDockTheFreePlaceStaysEmpty() {
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.h", "com.i", "com.j");
        List<String> dock = Arrays.asList("com.b", "com.c", "com.d");
        List<String> shown = sorted(tiles, dock, Collections.emptyMap());
        List<String> next = DockChoice.unpin(dock, "com.c");
        List<String> order = Order.gridKeys(DockChoice.placeKeys(keys(shown), Collections.singletonList(key("com.c")),
                DockChoice.unpinnedPlace(next.size(), SLOTS, false)), next);
        List<String> after = sorted(tiles, next, Order.ranks(new Order(order)));
        assertEquals(Arrays.asList("com.b", "com.d"), DockChoice.dockRow(after, next, SLOTS, false));
        assertEquals(2, after.indexOf("com.c"));   // first grid app, shown after the empty places

        // The last dock app out: the first row fills with grid apps, and it is not one of them.
        List<String> one = Collections.singletonList("com.b");
        List<String> none = DockChoice.unpin(one, "com.b");
        List<String> order2 = Order.gridKeys(DockChoice.placeKeys(keys(sorted(tiles, one, Collections.emptyMap())),
                Collections.singletonList(key("com.b")), DockChoice.unpinnedPlace(none.size(), SLOTS, false)), none);
        List<String> after2 = sorted(tiles, none, Order.ranks(new Order(order2)));
        assertFalse(DockChoice.dockRow(after2, none, SLOTS, false).contains("com.b"));
        assertEquals(SLOTS, after2.indexOf("com.b"));
    }

    // ---- the dock row and saving a row the owner arranged ----------------------------------------

    @Test
    public void dockRowFollowsDockSlots() {
        List<String> sorted = Arrays.asList("com.a", "com.b", "com.c", "com.x", "com.y", "com.z", "com.w");
        List<String> dock = Arrays.asList("com.a", "com.b", "com.c");
        assertEquals(sorted.subList(0, 6), DockChoice.dockRow(sorted, dock, 6, true));
        assertEquals(dock, DockChoice.dockRow(sorted, dock, 6, false));                    // empty places after them
        assertEquals(sorted.subList(0, 6), DockChoice.dockRow(sorted, Collections.emptyList(), 6, false));
        assertEquals(sorted.subList(0, 3), DockChoice.dockRow(sorted.subList(0, 3), dock, 6, false));   // nothing after
        assertEquals(Arrays.asList("com.a", "com.b"), DockChoice.dockRow(sorted.subList(0, 2), dock, 6, true));
    }

    @Test
    public void fromRowLeavesTheGridAppsFillingItsEndToFillDock() {
        List<String> keep = Arrays.asList("com.a", "com.b", "com.c");
        assertEquals(keep, DockChoice.fromRow(Arrays.asList("com.a", "com.b", "com.c", "com.x", "com.y"), keep));
        // An app the owner put between dock apps stays: fillers only ever follow the dock apps.
        assertEquals(Arrays.asList("com.a", "com.x", "com.b"), DockChoice.fromRow(Arrays.asList("com.a", "com.x", "com.b"), keep));
        assertEquals(Collections.emptyList(), DockChoice.fromRow(Arrays.asList("com.x", "com.y"), keep));
        List<String> moved = new ArrayList<>(keep);
        moved.add("com.y");   // carried into the row by the owner
        assertEquals(Arrays.asList("com.a", "com.b", "com.x", "com.y"),
                DockChoice.fromRow(Arrays.asList("com.a", "com.b", "com.x", "com.y", "com.z"), moved));
    }

    @Test
    public void aReorderedDockRowLooksTheSameOnceSaved() {
        // Five dock apps and com.g filling the last place; the owner swaps com.d and com.e in Reorder.
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.g", "com.h", "com.i");
        List<String> dock = THEME.subList(0, 5);
        List<String> keys = keys(Arrays.asList("com.a", "com.b", "com.c", "com.e", "com.d", "com.g", "com.h", "com.i"));
        List<String> row = Arrays.asList("com.a", "com.b", "com.c", "com.e", "com.d", "com.g");
        HashSet<String> keep = new HashSet<>(dock);
        keep.add("com.e");
        List<String> saved = DockChoice.fromRow(row, keep);
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.e", "com.d"), saved);   // com.g still only fills
        assertEquals(row, sorted(tiles, saved, Order.ranks(new Order(Order.gridKeys(keys, saved)))).subList(0, SLOTS));
    }

    // ---- an app leaving the dock without Remove from dock ----------------------------------------

    @Test
    public void anAppAThemeTakesOutOfTheDockDoesNotFillThePlaceItLeft() {
        // No dock.txt. Reorder moved com.h before com.g and saved under THEME; then a pushed theme leaves com.d out.
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.g", "com.h");
        List<String> reordered = keys(Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.h", "com.g"));
        List<String> order = Order.gridKeys(reordered, THEME);
        assertEquals(keys(Arrays.asList("com.h", "com.g")), order);
        List<String> pushed = Arrays.asList("com.a", "com.b", "com.c", "com.e", "com.f");
        List<String> after = sorted(tiles, pushed, Order.ranks(new Order(order)));
        // The owner's first grid app fills the free place; com.d joins the grid like a new install.
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.e", "com.f", "com.h"),
                DockChoice.dockRow(after, pushed, SLOTS, true));
        assertEquals(Arrays.asList("com.g", "com.d"), after.subList(SLOTS, tiles.size()));
        // Saved with its dock place's rank, com.d would fill that place and look pinned (its menu offers Pin).
        List<String> unsaved = sorted(tiles, pushed, Order.ranks(new Order(reordered)));
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.e", "com.f", "com.d"),
                DockChoice.dockRow(unsaved, pushed, SLOTS, true));
    }

    @Test
    public void resetDockDoesNotLeaveTheOwnersAppInAFreePlace() {
        // The owner's dock put com.x in place 0, and Reorder saved the order then; the theme has five dock apps.
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.g", "com.x");
        List<String> owner = Arrays.asList("com.x", "com.a", "com.b", "com.c", "com.d", "com.e");
        List<String> order = Order.gridKeys(keys(sorted(tiles, owner, Collections.emptyMap())), owner);
        List<String> theme = THEME.subList(0, 5);
        List<String> after = sorted(tiles, DockChoice.effective(null, theme), Order.ranks(new Order(order)));
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.g"),
                DockChoice.dockRow(after, theme, SLOTS, true));
    }

    // ---- an order.txt from before dock.txt -------------------------------------------------------

    @Test
    public void aVersion1OrderThatPutAnotherAppInTheDockKeepsThatDock() {
        // Edit dock swapped com.g in for com.f; the version 1 order ranked tiles before dock_apps.
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.g", "com.h");
        Map<String, Integer> ranks = Order.ranks(new Order(keys(Arrays.asList(
                "com.a", "com.b", "com.c", "com.d", "com.e", "com.g", "com.f", "com.h"))));
        List<String> oldRow = DockChoice.dockRow(sortedV1(tiles, THEME, ranks), THEME, SLOTS, true);
        List<String> newRow = DockChoice.dockRow(sorted(tiles, THEME, ranks), THEME, SLOTS, true);
        assertEquals(Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.g"), oldRow);
        assertEquals(THEME, newRow);
        List<String> kept = DockChoice.migrate(oldRow, newRow, THEME);
        assertNotNull(kept);
        assertEquals(oldRow, sorted(tiles, kept, ranks).subList(0, SLOTS));   // Home shows the same dock row
    }

    @Test
    public void aVersion1OrderWithAnAppMovedToTheFrontPinsIt() {
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.g", "com.h");
        Map<String, Integer> ranks = Order.ranks(new Order(keys(Arrays.asList(
                "com.g", "com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.h"))));
        List<String> oldRow = DockChoice.dockRow(sortedV1(tiles, THEME, ranks), THEME, SLOTS, true);
        List<String> kept = DockChoice.migrate(oldRow, DockChoice.dockRow(sorted(tiles, THEME, ranks), THEME, SLOTS, true), THEME);
        assertEquals(Arrays.asList("com.g", "com.a", "com.b", "com.c", "com.d", "com.e"), kept);
        assertEquals(oldRow, sorted(tiles, kept, ranks).subList(0, SLOTS));
    }

    @Test
    public void aVersion1OrderOfTheGridOnlyLeavesTheThemeInCharge() {
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.g", "com.h");
        Map<String, Integer> ranks = Order.ranks(new Order(keys(Arrays.asList(
                "com.a", "com.b", "com.c", "com.d", "com.e", "com.f", "com.h", "com.g"))));
        List<String> oldRow = DockChoice.dockRow(sortedV1(tiles, THEME, ranks), THEME, SLOTS, true);
        assertNull(DockChoice.migrate(oldRow, DockChoice.dockRow(sorted(tiles, THEME, ranks), THEME, SLOTS, true), THEME));
    }

    @Test
    public void aVersion1OrderWithAFillerKeepsItAsAFiller() {
        // com.f is not installed, so com.x filled the last place; the owner had swapped com.a and com.b.
        List<String> tiles = Arrays.asList("com.a", "com.b", "com.c", "com.d", "com.e", "com.x", "com.y");
        Map<String, Integer> ranks = Order.ranks(new Order(keys(Arrays.asList(
                "com.b", "com.a", "com.c", "com.d", "com.e", "com.x", "com.y"))));
        List<String> oldRow = DockChoice.dockRow(sortedV1(tiles, THEME, ranks), THEME, SLOTS, true);
        List<String> kept = DockChoice.migrate(oldRow, DockChoice.dockRow(sorted(tiles, THEME, ranks), THEME, SLOTS, true), THEME);
        assertEquals(Arrays.asList("com.b", "com.a", "com.c", "com.d", "com.e"), kept);
        assertEquals(oldRow, sorted(tiles, kept, ranks).subList(0, SLOTS));
        // Rewritten as version 2 without the dock apps: the same row, com.x still filling the last place.
        List<String> v2 = Order.gridKeys(keys(Arrays.asList("com.b", "com.a", "com.c", "com.d", "com.e", "com.x", "com.y")), kept);
        assertEquals(keys(Arrays.asList("com.x", "com.y")), v2);
        assertEquals(oldRow, sorted(tiles, kept, Order.ranks(new Order(v2))).subList(0, SLOTS));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static String key(String pkg) {
        return pkg + "/.Main";
    }

    private static List<String> keys(List<String> pkgs) {
        List<String> k = new ArrayList<>();
        for (String p : pkgs) k.add(key(p));
        return k;
    }

    /** Packages sorted as AppAdapter sorts tiles: dock apps in dock order, then the saved order, then by label. */
    private static List<String> sorted(List<String> pkgs, List<String> dock, Map<String, Integer> ranks) {
        List<String> t = new ArrayList<>(pkgs);
        t.sort((a, b) -> {
            int d = DockChoice.compare(dock, a, b);
            if (d != 0) return d;
            int s = Order.compare(ranks, key(a), key(b));
            return s != 0 ? s : a.compareTo(b);
        });
        return t;
    }

    /** The version 1 rule (AppAdapter before dock.txt): the saved order first, then dock_apps, then by label. */
    private static List<String> sortedV1(List<String> pkgs, List<String> dock, Map<String, Integer> ranks) {
        List<String> t = new ArrayList<>(pkgs);
        t.sort((a, b) -> {
            int s = Order.compare(ranks, key(a), key(b));
            if (s != 0) return s;
            int d = DockChoice.compare(dock, a, b);
            return d != 0 ? d : a.compareTo(b);
        });
        return t;
    }
}
