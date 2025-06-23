package com.memmcol.hes.repository;

import com.memmcol.hes.model.ObisMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeterRepository extends JpaRepository<ObisMapping, Long> {
    ObisMapping findByObisCode(String obisCode);
}
