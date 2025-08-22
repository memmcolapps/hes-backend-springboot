package com.memmcol.hes.repository;

import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.model.ModelProfileMetadataDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelProfileMetadataRepository
        extends JpaRepository<ModelProfileMetadata, Long> {

    List<ModelProfileMetadata> findByMeterModelAndProfileObis(
            String meterModel, String profileObis);

}
