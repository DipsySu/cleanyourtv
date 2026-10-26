package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class ThemeProfileTest {
    @Test public void normalAcceptsLegacyAndExternalThemes() {
        ThemeProfile normal = new ThemeProfile("", 0);
        assertFalse(normal.isBundled());
        assertTrue(normal.allows("legacy"));
        assertTrue(normal.allows("another-theme"));
        assertFalse(normal.allows(null));
        assertFalse(normal.needsBundledImport(0));
    }
    @Test public void companyRestrictsThemesAndUpgradesOnlyOlderVersions() {
        ThemeProfile company = new ThemeProfile("johnson-health-tech", 2);
        assertTrue(company.allows("johnson-health-tech"));
        assertFalse(company.allows("legacy"));
        assertFalse(company.allows("unknown"));
        assertTrue(company.needsBundledImport(0));
        assertTrue(company.needsBundledImport(1));
        assertFalse(company.needsBundledImport(2));
        assertFalse(company.needsBundledImport(3));
    }
}
