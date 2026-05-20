package com.memmcol.hes.domain.events;

/**
 * A household domain code (reason-of-operation or manage-token-type) with its lookup description.
 */
public record ResolvedHouseholdDomainCode(int code, String description) {
}
