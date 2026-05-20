package com.memmcol.hes.repository;

import com.memmcol.hes.entities.HouseholdReasonOfOperationLookup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdReasonOfOperationLookupRepository
        extends JpaRepository<HouseholdReasonOfOperationLookup, Integer> {
}
