package com.memmcol.hes.repository;

import com.memmcol.hes.entities.HouseholdFraudEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdFraudEventRepository extends JpaRepository<HouseholdFraudEvent, Long> {
}
