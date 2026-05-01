package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.dto.BillingDataHouseholdDTO;
import com.memmcol.hes.entities.BillingHouseholdId;
import com.memmcol.hes.entities.BillingHouseholdToEntity;
import com.memmcol.hes.entities.DailyBillingDataHouseholdEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DailyBillingDataHouseholdPersistenceAdapter extends AbstractBillingHouseholdPersistenceAdapter<BillingDataHouseholdDTO, DailyBillingDataHouseholdEntity, BillingHouseholdId> {
    @PersistenceContext
    private final EntityManager em;
    private final ProfileStatePort statePort;

    @Override
    protected EntityManager em() {
        return em;
    }

    @Override
    protected ProfileStatePort statePort() {
        return statePort;
    }

    @Override
    protected Class<DailyBillingDataHouseholdEntity> entityClass() {
        return DailyBillingDataHouseholdEntity.class;
    }

    @Override
    protected String baseTableName() {
        return "daily_billing_data_hh";
    }

    @Override
    protected String partitionPrefix() {
        return "daily_billing_data_hh";
    }

    @Override
    protected LocalDateTime entryTimestamp(BillingDataHouseholdDTO dto) {
        return dto.getEntryTimestamp();
    }

    @Override
    protected DailyBillingDataHouseholdEntity toEntity(BillingDataHouseholdDTO dto) {
        return BillingHouseholdToEntity.toDailyData(dto);
    }

    @Override
    protected BillingHouseholdId toId(DailyBillingDataHouseholdEntity entity) {
        return new BillingHouseholdId(entity.getMeterSerial(), entity.getEntryTimestamp());
    }

    @Override
    protected LocalDateTime stateAdvanceTo(LocalDateTime advanceTo) {
        return advanceTo.plusDays(1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial, String profileObis, List<BillingDataHouseholdDTO> readings, CapturePeriod cp) {
        return super.saveBatchAndAdvanceCursor(meterSerial, profileObis, readings, cp, "DailyBillingDataHouseholdEntity");
    }
}

