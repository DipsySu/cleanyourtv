package com.gathin.tvhome;

/** Pure geometry for the mixed-width dock and application grid.  Units are GridLayout spans. */
final class GridGeometry {
    private GridGeometry() { }

    static int lcm(int a, int b) {
        a = Math.max(1, a); b = Math.max(1, b);
        return a / gcd(a, b) * b;
    }

    static int spanCount(int dockColumns, int gridColumns) { return lcm(dockColumns, gridColumns); }
    static int footerSpan(int dockColumns, int gridColumns) { return spanCount(dockColumns, gridColumns); }
    static int dockSpan(int dockColumns, int gridColumns) { return spanCount(dockColumns, gridColumns) / Math.max(1, dockColumns); }
    static int gridSpan(int dockColumns, int gridColumns) { return spanCount(dockColumns, gridColumns) / Math.max(1, gridColumns); }
    static int spanAt(int position, int dockColumns, int gridColumns, boolean dockRow) {
        return dockRow && position < dockColumns ? dockSpan(dockColumns, gridColumns) : gridSpan(dockColumns, gridColumns);
    }

    static boolean isDock(int position, int dockColumns, boolean dockRow) {
        return dockRow && position >= 0 && position < dockColumns;
    }

    /** First adapter position in the visual row containing {@code position}. */
    static int rowStart(int position, int dockColumns, int gridColumns, boolean dockRow) {
        if (position < 0) return -1;
        if (isDock(position, dockColumns, dockRow)) return 0;
        int first = dockRow ? dockColumns : 0;
        return first + ((position - first) / Math.max(1, gridColumns)) * Math.max(1, gridColumns);
    }

    static int rowEnd(int position, int count, int dockColumns, int gridColumns, boolean dockRow) {
        if (count <= 0 || position < 0 || position >= count) return -1;
        int start = rowStart(position, dockColumns, gridColumns, dockRow);
        int width = isDock(position, dockColumns, dockRow) ? dockColumns : Math.max(1, gridColumns);
        return Math.min(count - 1, start + width - 1);
    }

    /** Logical horizontal boundaries, mirrored when the grid is RTL. */
    static boolean atHorizontalEdge(int position, int count, int dockColumns, int gridColumns,
                                    boolean dockRow, boolean rtl, boolean left) {
        if (position < 0 || position >= count) return true;
        int start = rowStart(position, dockColumns, gridColumns, dockRow);
        int end = rowEnd(position, count, dockColumns, gridColumns, dockRow);
        return left == rtl ? position == end : position == start;
    }

    /**
     * Row above/below mapped by the nearest visual centre.  This avoids assuming that a six-wide
     * dock and a four-wide application row share column indices.
     */
    static int verticalNearestCenter(int position, int count, int dockColumns, int gridColumns,
                                     boolean dockRow, boolean up) {
        if (count <= 0 || position < 0 || position >= count) return -1;
        int currentStart = rowStart(position, dockColumns, gridColumns, dockRow);
        int targetStart;
        int targetWidth;
        if (up) {
            if (currentStart == 0) return -1;
            if (dockRow && currentStart == dockColumns) { targetStart = 0; targetWidth = dockColumns; }
            else { targetWidth = Math.max(1, gridColumns); targetStart = currentStart - targetWidth; }
        } else {
            int currentWidth = isDock(position, dockColumns, dockRow) ? dockColumns : Math.max(1, gridColumns);
            targetStart = currentStart + currentWidth;
            if (targetStart >= count) return -1;
            targetWidth = Math.max(1, gridColumns);
        }
        int targetEnd = Math.min(count - 1, targetStart + targetWidth - 1);
        float center = visualCenter(position, dockColumns, gridColumns, dockRow);
        int best = targetStart;
        float distance = Float.MAX_VALUE;
        for (int candidate = targetStart; candidate <= targetEnd; candidate++) {
            float d = Math.abs(visualCenter(candidate, dockColumns, gridColumns, dockRow) - center);
            if (d < distance) { distance = d; best = candidate; }
        }
        return best;
    }

    static int nearestInRow(int sourcePosition, int targetStart, int count, int dockColumns,
                            int gridColumns, boolean dockRow) {
        if (sourcePosition < 0 || targetStart < 0 || targetStart >= count) return -1;
        int end = rowEnd(targetStart, count, dockColumns, gridColumns, dockRow);
        float center = visualCenter(sourcePosition, dockColumns, gridColumns, dockRow);
        int best = targetStart;
        float distance = Float.MAX_VALUE;
        for (int candidate = targetStart; candidate <= end; candidate++) {
            float d = Math.abs(visualCenter(candidate, dockColumns, gridColumns, dockRow) - center);
            if (d < distance) { distance = d; best = candidate; }
        }
        return best;
    }

    private static float visualCenter(int position, int dockColumns, int gridColumns, boolean dockRow) {
        boolean dock = isDock(position, dockColumns, dockRow);
        int columns = dock ? Math.max(1, dockColumns) : Math.max(1, gridColumns);
        int start = dock ? 0 : (dockRow ? dockColumns : 0);
        return ((position - start) % columns + .5f) / columns;
    }

    private static int gcd(int a, int b) { while (b != 0) { int t = a % b; a = b; b = t; } return a; }
}
