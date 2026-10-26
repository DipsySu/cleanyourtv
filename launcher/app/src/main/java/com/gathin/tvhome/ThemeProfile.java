package com.gathin.tvhome;

/** Theme eligibility and bundled package upgrade policy, independent of Android storage. */
final class ThemeProfile {
    private final String bundledId;
    private final int bundledVersion;
    ThemeProfile(String bundledId, int bundledVersion) { this.bundledId = bundledId; this.bundledVersion = bundledVersion; }
    boolean isBundled() { return !bundledId.isEmpty(); }
    String bundledId() { return bundledId; }
    int bundledVersion() { return bundledVersion; }
    boolean allows(String id) { return id != null && (!isBundled() || bundledId.equals(id)); }
    boolean needsBundledImport(int installedVersion) { return isBundled() && installedVersion < bundledVersion; }
}
