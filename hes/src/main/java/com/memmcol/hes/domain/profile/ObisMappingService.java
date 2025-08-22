package com.memmcol.hes.domain.profile;

import com.memmcol.hes.model.ObisMapping;
import com.memmcol.hes.repository.ObisMappingRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ObisMappingService {

    private final ObisMappingRepository obisMappingRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = "modelScalerMap", key = "#model")
    public Map<String, MultiplierDTO> getScalerAndPurposeMap(String model) {
        List<ObisMapping> mappings = obisMappingRepository.findByModel(model);
        return mappings.stream()
                .filter(m -> m.getScaler() != null)
                .collect(Collectors.toMap(
                        ObisMapping::getObisCodeCombined,
                        m -> new MultiplierDTO(m.getScaler(), m.getPurpose(), m.getDescription()),
                        (existing, replacement) -> existing
                ));
    }

    @Cacheable(value = "modelScalerMap", key = "#model")
    public Map<String, Double> getScalersForModel(String model) {
        List<ObisMapping> mappings = obisMappingRepository.findByModel(model);
        return mappings.stream()
                .filter(m -> m.getScaler() != null)
                .collect(Collectors.toMap(
                        ObisMapping::getObisCodeCombined,
                        ObisMapping::getScaler,
                        (existing, replacement) -> existing
                ));
    }

    @Cacheable(value = "modelDescriptionMap", key = "#model")
    public Map<String, String> getDescriptionForModel(String model) {
        List<ObisMapping> mappings = obisMappingRepository.findByModel(model);
        return mappings.stream()
                .collect(Collectors.toMap(
                        ObisMapping::getObisCodeCombined,
                        ObisMapping::getDescription,
                        (existing, replacement) -> existing
                ));
    }

    @Cacheable(cacheNames = "obisMappings", key = "T(java.lang.String).format('%s|%s', #model, #purpose)")
    public List<ObisMapping> getMappingsByModelAndPurpose(String model, String purpose) {
        return obisMappingRepository.findByModelAndPurpose(model, purpose);
    }

    @PostConstruct
    public void preloadObisMappings() {
        List<ObisMapping> allMappings = obisMappingRepository.findAll();

        // Group all entries by model only
        Map<String, List<ObisMapping>> mappingsByModel = allMappings.stream()
                .collect(Collectors.groupingBy(ObisMapping::getModel));

        // Put each group into the cache
        mappingsByModel.forEach((model, mappings) -> {
            cacheManager.getCache("obisMappings").put(model, mappings);
        });

        log.info("✅ OBIS mappings preloaded into 'obisMappings' cache grouped by model.");
    }

    @Cacheable(cacheNames = "obisMappings", key = "#model")
    public List<ObisMapping> getMappingsByModel(String model) {
        return obisMappingRepository.findByModel(model);
    }
}
