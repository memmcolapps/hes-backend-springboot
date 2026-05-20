package com.memmcol.hes.cache;

import com.memmcol.hes.domain.events.HouseholdDomainCodeParser;
import com.memmcol.hes.domain.events.ResolvedHouseholdDomainCode;
import com.memmcol.hes.entities.HouseholdManageTokenTypeLookup;
import com.memmcol.hes.entities.HouseholdReasonOfOperationLookup;
import com.memmcol.hes.repository.HouseholdManageTokenTypeLookupRepository;
import com.memmcol.hes.repository.HouseholdReasonOfOperationLookupRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves household-specific domain codes (not DLMS {@code event_code}) from lookup tables:
 * {@code household_reason_of_operation_lookup}, {@code household_manage_token_type_lookup}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdDomainCodeLookupService {

    private final HouseholdReasonOfOperationLookupRepository reasonOfOperationRepository;
    private final HouseholdManageTokenTypeLookupRepository manageTokenTypeRepository;

    private final Map<Integer, String> reasonOfOperationByCode = new HashMap<>();
    private final Map<Integer, String> manageTokenTypeByCode = new HashMap<>();

    @PostConstruct
    public void loadAtStartup() {
        reasonOfOperationByCode.clear();
        manageTokenTypeByCode.clear();

        for (HouseholdReasonOfOperationLookup row : reasonOfOperationRepository.findAll()) {
            reasonOfOperationByCode.put(row.getCode(), row.getDescription());
        }
        for (HouseholdManageTokenTypeLookup row : manageTokenTypeRepository.findAll()) {
            manageTokenTypeByCode.put(row.getCode(), row.getDescription());
        }

        log.info("Household domain code lookups loaded: {} reason-of-operation, {} manage-token-type",
                reasonOfOperationByCode.size(), manageTokenTypeByCode.size());
    }

    public Optional<ResolvedHouseholdDomainCode> resolveReasonOfOperation(Object rawFromMeter) {
        return resolve(rawFromMeter, reasonOfOperationByCode);
    }

    public Optional<ResolvedHouseholdDomainCode> resolveManageTokenType(Object rawFromMeter) {
        return resolve(rawFromMeter, manageTokenTypeByCode);
    }

    private Optional<ResolvedHouseholdDomainCode> resolve(Object raw, Map<Integer, String> descriptionsByCode) {
        Integer code = HouseholdDomainCodeParser.parseCode(raw);
        if (code == null) {
            return Optional.empty();
        }
        String description = descriptionsByCode.get(code);
        if (description == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedHouseholdDomainCode(code, description));
    }

    @Scheduled(cron = "0 30 2 * * ?")
    public void scheduledRefresh() {
        loadAtStartup();
    }

    public void refresh() {
        loadAtStartup();
    }
}
