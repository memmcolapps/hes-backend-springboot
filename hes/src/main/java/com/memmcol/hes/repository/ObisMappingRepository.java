package com.memmcol.hes.repository;

import com.memmcol.hes.model.ObisMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ObisMappingRepository extends JpaRepository<ObisMapping, Long> {
    boolean existsByModelAndObisCodeCombined(String model, String obisCodeCombined);
    List<ObisMapping> findByModel(String model);
    List<ObisMapping> findByModelAndPurpose(String model, String purpose);

    /*
    *   select * from obis_mapping
        where model = 'MMX-313-CT'
        and class_id in (4, 3)
        and attribute_index = 2;
    * */
    List<ObisMapping> findByModelAndClassIdInAndAttributeIndex(
            String model,
            List<Integer> classIds,
            Integer attributeIndex
    );

}
