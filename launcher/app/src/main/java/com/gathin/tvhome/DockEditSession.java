package com.gathin.tvhome;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Snapshot and persistence policy for a dock edit after temporary empty slots have been removed. */
final class DockEditSession {
    final int slots;
    private final List<String> originalRow;
    private final List<String> originalDock;
    private final Set<String> kept;

    DockEditSession(int slots, List<String> row, List<String> pinned) {
        this.slots = slots;
        originalRow = DockChoice.of(row);
        originalDock = DockChoice.of(pinned);
        kept = new HashSet<>(originalDock);
    }

    void touch(String pkg) {
        if (pkg != null) kept.add(pkg);
    }

    /** Null means no dock change, including opening/cancelling an editor with a sparse dock. */
    List<String> dockAfter(List<String> row) {
        if (originalRow.equals(row)) return null;
        List<String> next = DockChoice.fromRow(row, kept);
        return originalDock.equals(next) ? null : next;
    }
}
