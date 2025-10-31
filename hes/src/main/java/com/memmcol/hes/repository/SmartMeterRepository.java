package com.memmcol.hes.repository;

import com.memmcol.hes.model.SmartMeterInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SmartMeterRepository extends JpaRepository<SmartMeterInfo, UUID> {
    @Query("SELECT COUNT(m) FROM SmartMeterInfo m")
    int countAll();
}
