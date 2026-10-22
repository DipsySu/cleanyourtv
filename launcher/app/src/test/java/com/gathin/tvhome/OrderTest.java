package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the saved tile order: file format (version 2, and version 1 still read) and the sort step. */
public class OrderTest {

    private static final List<String> DOCK = Arrays.asList("com.plexapp.android", "com.netflix.ninja");

    @Test
    public void roundTrips() {
        Order o = new Order(Arrays.asList("b/.B", "a/.A", "c/.C"));
        Order back = Order.decode(Order.encode(o));
        assertNull(back.legacyDock);
        assertEquals(o.keys, back.keys);
    }

    @Test
    public void emptyOrderRoundTrips() {
        Order back = Order.decode(Order.encode(new Order(Collections.emptyList())));
        assertNull(back.legacyDock);
        assertTrue(back.keys.isEmpty());
    }

    @Test
    public void writesVersion2WithoutTheDockLine() {
        Order v1 = Order.decode("tvhome-order 1\ndock com.plexapp.android,com.netflix.ninja\na/.A\n");
        assertEquals("tvhome-order 2\na/.A\n", Order.encode(v1));
    }

    @Test
    public void readsVersion1WithTheDockItWasSavedUnder() {
        Order o = Order.decode("tvhome-order 1\ndock " + Order.dockKey(DOCK) + "\nb/.B\na/.A\n");
        assertEquals("com.plexapp.android,com.netflix.ninja", o.legacyDock);
        assertEquals(Arrays.asList("b/.B", "a/.A"), o.keys);
        assertEquals("", Order.decode("tvhome-order 1\ndock \n").legacyDock);   // saved with no dock_apps
    }

    @Test
    public void rejectsWhatIsNotAnOrder() {
        assertNull(Order.decode(null));
        assertNull(Order.decode(""));
        assertNull(Order.decode("{\"version\":2}"));
        assertNull(Order.decode("tvhome-order 3\na/.A\n"));
        assertNull(Order.decode("tvhome-order 2\ndock x\na/.A\n"));          // a version 1 body under a version 2 header
        assertNull(Order.decode("tvhome-order 1\na/.A\n"));                 // version 1 without its dock line
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\n\nb/.B\n"));  // blank key in the middle
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\na/.A\n"));    // duplicate key
        assertNull(Order.decode("tvhome-order 2\na/.A\n\nb/.B\n"));
        assertNull(Order.decode("tvhome-order 2\na/.A\na/.A\n"));
        assertNull(Order.decode("tvhome-order 2\nnoslash\n"));
        assertNull(Order.decode("tvhome-order 2\n/.A\n"));
        assertNull(Order.decode("tvhome-order 2\na/\n"));
        assertNull(Order.decode("tvhome-order 2\na/.A\r\n"));
    }

    @Test
    public void keysAreFlattenedComponents() {
        assertTrue(Order.validKey("com.netflix.ninja/.MainActivity"));
        assertTrue(Order.validKey("com.netflix.ninja/com.netflix.ninja.MainActivity"));
        assertFalse(Order.validKey("com.netflix.ninja"));
        assertFalse(Order.validKey("dock com.netflix.ninja"));
        assertFalse(Order.validKey(""));
    }

    @Test
    public void ranksFollowTheSavedOrder() {
        Order o = new Order(Arrays.asList("b/.B", "a/.A"));
        Map<String, Integer> r = Order.ranks(o);
        assertEquals(Integer.valueOf(0), r.get("b/.B"));
        assertEquals(Integer.valueOf(1), r.get("a/.A"));
        assertTrue(Order.ranks(null).isEmpty());
    }

    @Test
    public void aVersion1OrderAppliesOnlyUnderTheDockAppsItWasSavedWith() {
        Order same = Order.decode("tvhome-order 1\ndock com.plexapp.android,com.netflix.ninja\nb/.B\na/.A\n");
        assertTrue(Order.applies(same, DOCK));
        // Saved under other dock_apps (a pushed theme, a rebuild): ignored, as before, since it ranked its dock row.
        Order other = Order.decode("tvhome-order 1\ndock com.netflix.ninja,com.plexapp.android\nb/.B\na/.A\n");
        assertFalse(Order.applies(other, DOCK));
        // A version 2 order leaves the dock apps out, so it applies under any dock_apps.
        assertTrue(Order.applies(new Order(Arrays.asList("b/.B", "a/.A")), DOCK));
        assertTrue(Order.applies(new Order(Arrays.asList("b/.B", "a/.A")), Collections.emptyList()));
        assertFalse(Order.applies(null, DOCK));
    }

    @Test
    public void gridKeysLeaveOutEveryTileOfTheDockApps() {
        List<String> keys = Arrays.asList("com.netflix.ninja/.Main", "a/.A", "com.plexapp.android/.Main",
                "com.netflix.ninja/.Kids", "com.netflix.ninja2/.Main", "b/.B");
        assertEquals(Arrays.asList("a/.A", "com.netflix.ninja2/.Main", "b/.B"), Order.gridKeys(keys, DOCK));
        assertEquals(keys, Order.gridKeys(keys, Collections.emptyList()));
        assertEquals("com.netflix.ninja", Order.packageOf("com.netflix.ninja/com.netflix.ninja.MainActivity"));
        assertEquals("com.netflix.ninja", Order.packageOf("com.netflix.ninja/.MainActivity"));
    }

    @Test
    public void savedTilesComeFirstAndNewOnesKeepTheirOrderAfter() {
        Order o = new Order(Arrays.asList("c/.C", "a/.A", "gone/.Gone"));
        Map<String, Integer> r = Order.ranks(o);
        // "n" and "m" are new installs: unranked, so the usual rule (here: list order) decides between them.
        List<String> tiles = new ArrayList<>(Arrays.asList("m/.M", "a/.A", "n/.N", "c/.C"));
        List<String> usual = new ArrayList<>(tiles);
        tiles.sort((x, y) -> {
            int c = Order.compare(r, x, y);
            return c != 0 ? c : Integer.compare(usual.indexOf(x), usual.indexOf(y));
        });
        assertEquals(Arrays.asList("c/.C", "a/.A", "m/.M", "n/.N"), tiles);
    }
}
