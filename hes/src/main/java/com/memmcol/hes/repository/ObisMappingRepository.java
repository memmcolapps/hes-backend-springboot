package com.memmcol.hes.repository;

import com.memmcol.hes.model.ObisMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ObisMappingRepository extends JpaRepository<ObisMapping, Long> {
    ObisMapping findByObisCode(String obisCode);
    boolean existsByObisCodeCombined(String obisCodeCombined);
    boolean existsByModelAndObisCodeCombined(String model, String obisCodeCombined);
    List<ObisMapping> findByModel(String model);
    List<ObisMapping> findByModelAndPurpose(String model, String purpose);
}
