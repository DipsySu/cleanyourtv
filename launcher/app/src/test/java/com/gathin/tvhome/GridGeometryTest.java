package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GridGeometryTest {
    @Test public void sixDockAndFourGridUseLcmSpansAndStartGridAtSix() {
        assertEquals(12, GridGeometry.spanCount(6, 4));
        assertEquals(2, GridGeometry.dockSpan(6, 4));
        assertEquals(3, GridGeometry.gridSpan(6, 4));
        assertEquals(12, GridGeometry.footerSpan(6, 4));
        assertEquals(2, GridGeometry.spanAt(5, 6, 4, true));
        assertEquals(3, GridGeometry.spanAt(6, 6, 4, true));
        assertEquals(6, GridGeometry.rowStart(6, 6, 4, true));
        assertEquals(9, GridGeometry.rowEnd(6, 20, 6, 4, true));
    }

    @Test public void sixDockAndSixGridRemainSingleRows() {
        assertEquals(6, GridGeometry.spanCount(6, 6));
        assertEquals(1, GridGeometry.dockSpan(6, 6));
        assertEquals(1, GridGeometry.gridSpan(6, 6));
        assertEquals(0, GridGeometry.rowStart(5, 6, 6, true));
        assertEquals(6, GridGeometry.rowStart(6, 6, 6, true));
    }

    @Test public void verticalUsesNormalizedVisualCentersAcrossSixAndFour() {
        // Dock centre 2.5/6 is nearest grid centre 1.5/4, not the same ordinal column.
        assertEquals(7, GridGeometry.verticalNearestCenter(2, 22, 6, 4, true, false));
        assertEquals(2, GridGeometry.verticalNearestCenter(7, 22, 6, 4, true, true));
    }

    @Test public void shortAndPartialRowsHaveSafeNearestTargets() {
        assertEquals(-1, GridGeometry.verticalNearestCenter(0, 0, 6, 4, true, false));
        assertEquals(-1, GridGeometry.verticalNearestCenter(0, 1, 6, 4, true, true));
        // Six dock slots plus ten apps: the final app row is positions 14 and 15.
        assertEquals(14, GridGeometry.verticalNearestCenter(10, 16, 6, 4, true, false));
        assertEquals(-1, GridGeometry.verticalNearestCenter(14, 16, 6, 4, true, false));
    }

    @Test public void horizontalBoundariesMirrorForRtlAndPlainGridHasNoDock() {
        assertTrue(GridGeometry.atHorizontalEdge(0, 6, 6, 4, true, false, true));
        assertTrue(GridGeometry.atHorizontalEdge(5, 6, 6, 4, true, true, true));
        assertFalse(GridGeometry.isDock(0, 6, false));
        assertEquals(0, GridGeometry.rowStart(0, 6, 4, false));
        assertEquals(4, GridGeometry.rowStart(4, 6, 4, false));
        assertEquals(3, GridGeometry.spanAt(0, 6, 4, false));
    }

    @Test public void eachDockColumnMapsToItsNearestFourColumnCellAndBack() {
        int[] down = {6, 6, 7, 8, 8, 9};
        int[] up = {0, 2, 3, 5};
        for (int dock = 0; dock < down.length; dock++) {
            assertEquals("dock " + dock, down[dock],
                    GridGeometry.verticalNearestCenter(dock, 16, 6, 4, true, false));
        }
        for (int col = 0; col < up.length; col++) {
            assertEquals("grid " + col, up[col],
                    GridGeometry.verticalNearestCenter(6 + col, 16, 6, 4, true, true));
        }
    }

    @Test public void explicitRowsKeepNavigationAndReorderWithinTheirBoundaries() {
        verifyRows(4, true, new int[][] {{0,1,2,3,4,5}, {6,7,8,9}, {10,11,12,13}, {14,15}});
        verifyRows(6, true, new int[][] {{0,1,2,3,4,5}, {6,7,8,9,10,11}, {12,13,14,15}});
        verifyRows(4, false, new int[][] {{0,1,2,3}, {4,5,6,7}, {8,9}});
        verifyRows(4, true, new int[][] {{0,1,2,3,4}});
    }

    private static void verifyRows(int columns, boolean dockRow, int[][] rows) {
        int[] lastRow = rows[rows.length - 1];
        int count = lastRow[lastRow.length - 1] + 1;
        for (int row = 0; row < rows.length; row++) {
            int start = rows[row][0], end = rows[row][rows[row].length - 1];
            for (int pos : rows[row]) {
                assertEquals(start, GridGeometry.rowStart(pos, 6, columns, dockRow));
                assertEquals(end, GridGeometry.rowEnd(pos, count, 6, columns, dockRow));
                for (boolean rtl : new boolean[] {false, true}) {
                    assertEquals(pos == start, GridGeometry.atHorizontalEdge(pos, count, 6, columns, dockRow, rtl, !rtl));
                    assertEquals(pos == end, GridGeometry.atHorizontalEdge(pos, count, 6, columns, dockRow, rtl, rtl));
                }
                for (boolean up : new boolean[] {false, true}) {
                    int target = GridGeometry.verticalNearestCenter(pos, count, 6, columns, dockRow, up);
                    int adjacent = row + (up ? -1 : 1);
                    if (adjacent < 0 || adjacent >= rows.length) assertEquals(-1, target);
                    else {
                        int[] expectedRow = rows[adjacent];
                        assertTrue(target >= expectedRow[0] && target <= expectedRow[expectedRow.length - 1]);
                    }
                }
            }
        }
    }

    @Test public void footerReturnsToLastTileWhenOnlyAPartialDockExists() {
        assertEquals(4, GridGeometry.nearestInRow(4, 0, 5, 6, 4, true));
        assertEquals(-1, GridGeometry.nearestInRow(0, 0, 0, 6, 4, true));
        assertEquals(15, GridGeometry.verticalNearestCenter(11, 16, 6, 6, true, false));
        assertEquals(15, GridGeometry.verticalNearestCenter(13, 16, 6, 4, true, false));
    }
}
