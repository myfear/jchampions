package util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JavaExtensionsTest {
    @Test
    void usesTheYearFromTheSchedule() {
        assertEquals("22 January 2027", JavaExtensions.formatDate("2027-01-22"));
        assertEquals("18 January 2028", JavaExtensions.formatDate("2028-01-18"));
    }

    @Test
    void preservesUndatedAndMissingScheduleValues() {
        assertEquals("Thursday, 22-Jan", JavaExtensions.formatDate("Thursday, 22-Jan"));
        assertEquals("", JavaExtensions.formatDate(null));
        assertEquals("", JavaExtensions.formatDate(""));
    }

    @Test
    void shrinksLongHeadingsToFitTheCard() {
        assertEquals(62, CardLayout.fontSize("Holly Cummins", 62, 34, 656, 2));
        int size = CardLayout.fontSize(
                "Building resilient Java applications: practical lessons in distributed systems, artificial intelligence, "
                        + "and the engineering trade-offs behind reliable production systems",
                62, 34, 968, 4);
        assertTrue(size >= 34 && size < 62);
    }
}
