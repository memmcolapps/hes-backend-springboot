package com.memmcol.hes.domain.events;

/**
 * OBIS codes for a two-tier event read: non-household (MD/CT and similar) vs household models.
 */
public record ResolvedTieredEventObis(String mdCtObis, String householdObis) {
}
