package com.memmcol.hes.repository;

import com.memmcol.hes.entities.HouseholdManageTokenTypeLookup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdManageTokenTypeLookupRepository
        extends JpaRepository<HouseholdManageTokenTypeLookup, Integer> {
}
