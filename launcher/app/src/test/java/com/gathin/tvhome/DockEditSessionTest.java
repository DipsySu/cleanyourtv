package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DockEditSessionTest {
    private static List<String> apps(String... suffixes) {
        List<String> values = new ArrayList<>();
        for (String suffix : suffixes) values.add("com." + suffix);
        return values;
    }

    @Test public void aSparseDockPersistsAnAppChosenForItsFourthVisibleSlot() {
        List<String> tiles = apps("a", "b", "c", "d", "e", "f", "g");
        DockEditSession edit = new DockEditSession(6, tiles.subList(0, 6), apps("a", "b"));
        edit.touch("com.g");
        // The same remove/insert swap used by MainActivity.swapIntoDock.
        String displaced = tiles.get(3);
        tiles.add(3, tiles.remove(6));
        tiles.add(6, tiles.remove(tiles.indexOf(displaced)));
        List<String> saved = edit.dockAfter(tiles.subList(0, edit.slots));
        assertEquals(apps("a", "b", "c", "g"), saved);
        assertEquals(saved, DockChoice.decode(DockChoice.encode(saved)));
        // The next reload keeps g at the actual fourth position, with two empty places afterwards.
        assertEquals(2, DockSlots.empty(saved.size(), edit.slots, tiles.size(), false));
    }

    @Test public void cancellingAnUnchangedSparseDockDoesNotSaveItsTemporaryFillers() {
        List<String> row = apps("a", "b", "c", "d", "e", "f");
        DockEditSession edit = new DockEditSession(6, row, apps("a", "b"));
        assertNull(edit.dockAfter(row));
        edit.touch("com.e");
        assertNull(edit.dockAfter(row));
    }

    @Test public void aGridOnlyMoveDoesNotCreateAnOwnerDockFile() {
        DockEditSession edit = new DockEditSession(6, apps("a", "b", "c", "d", "e", "f"), apps("a", "b"));
        edit.touch("com.g");
        assertNull(edit.dockAfter(apps("a", "b", "d", "c", "e", "f")));
    }

    @Test public void movingAPinOutOfTheRowRemovesItFromTheSavedDock() {
        DockEditSession edit = new DockEditSession(4, apps("a", "b", "c", "d"), apps("a", "b"));
        edit.touch("com.a");
        assertEquals(apps("b"), edit.dockAfter(apps("b", "c", "d", "e")));
    }

    @Test public void aFullDockKeepsTheSwapOrder() {
        List<String> pinned = apps("a", "b", "c", "d", "e", "f");
        DockEditSession edit = new DockEditSession(6, pinned, pinned);
        edit.touch("com.g");
        assertEquals(apps("a", "b", "c", "g", "e", "f"),
                edit.dockAfter(apps("a", "b", "c", "g", "e", "f")));
    }

    @Test public void snapshotsDoNotChangeWhenTheAdaptersListsAreEdited() {
        List<String> row = apps("a", "b", "c", "d");
        List<String> pinned = apps("a", "b");
        DockEditSession edit = new DockEditSession(4, row, pinned);
        row.set(0, "com.e");
        pinned.clear();
        edit.touch("com.e");
        assertEquals(apps("e", "b"), edit.dockAfter(row));
    }

    @Test public void fewerAppsThanSlotsAndAnEmptyDockAreSafe() {
        DockEditSession edit = new DockEditSession(6, apps("a", "b"), Collections.emptyList());
        edit.touch("com.b");
        assertEquals(apps("b"), edit.dockAfter(apps("b", "a")));
    }
}
