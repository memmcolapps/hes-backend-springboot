package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.dto.BillingDataHouseholdDTO;
import com.memmcol.hes.entities.BillingHouseholdId;
import com.memmcol.hes.entities.BillingHouseholdToEntity;
import com.memmcol.hes.entities.MonthlyBillingDataHouseholdEntity;
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
public class MonthlyBillingDataHouseholdPersistenceAdapter extends AbstractBillingHouseholdPersistenceAdapter<BillingDataHouseholdDTO, MonthlyBillingDataHouseholdEntity, BillingHouseholdId> {
    @PersistenceContext
    private EntityManager em;
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
    protected Class<MonthlyBillingDataHouseholdEntity> entityClass() {
        return MonthlyBillingDataHouseholdEntity.class;
    }

    @Override
    protected String baseTableName() {
        return "monthly_billing_data_hh";
    }

    @Override
    protected String partitionPrefix() {
        return "monthly_billing_data_hh";
    }

    @Override
    protected LocalDateTime entryTimestamp(BillingDataHouseholdDTO dto) {
        return dto.getEntryTimestamp();
    }

    @Override
    protected MonthlyBillingDataHouseholdEntity toEntity(BillingDataHouseholdDTO dto) {
        return BillingHouseholdToEntity.toMonthlyData(dto);
    }

    @Override
    protected BillingHouseholdId toId(MonthlyBillingDataHouseholdEntity entity) {
        return new BillingHouseholdId(entity.getMeterSerial(), entity.getEntryTimestamp());
    }

    @Override
    protected String meterModelFromFirstDto(BillingDataHouseholdDTO dto) {
        return dto != null ? dto.getMeterModel() : null;
    }

    @Override
    protected LocalDateTime stateAdvanceTo(LocalDateTime advanceTo) {
        return advanceTo.plusMonths(1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial, String profileObis, List<BillingDataHouseholdDTO> readings, CapturePeriod cp) {
        return super.saveBatchAndAdvanceCursor(meterSerial, profileObis, readings, cp, "MonthlyBillingDataHouseholdEntity");
    }
}

