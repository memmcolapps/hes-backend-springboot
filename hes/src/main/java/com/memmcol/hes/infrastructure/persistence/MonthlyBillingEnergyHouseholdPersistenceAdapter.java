package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.dto.BillingEnergyHouseholdDTO;
import com.memmcol.hes.entities.BillingHouseholdId;
import com.memmcol.hes.entities.BillingHouseholdToEntity;
import com.memmcol.hes.entities.MonthlyBillingEnergyHouseholdEntity;
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
public class MonthlyBillingEnergyHouseholdPersistenceAdapter extends AbstractBillingHouseholdPersistenceAdapter<BillingEnergyHouseholdDTO, MonthlyBillingEnergyHouseholdEntity, BillingHouseholdId> {
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
    protected Class<MonthlyBillingEnergyHouseholdEntity> entityClass() {
        return MonthlyBillingEnergyHouseholdEntity.class;
    }

    @Override
    protected String baseTableName() {
        return "monthly_billing_energy_hh";
    }

    @Override
    protected String partitionPrefix() {
        return "monthly_billing_energy_hh";
    }

    @Override
    protected LocalDateTime entryTimestamp(BillingEnergyHouseholdDTO dto) {
        return dto.getEntryTimestamp();
    }

    @Override
    protected MonthlyBillingEnergyHouseholdEntity toEntity(BillingEnergyHouseholdDTO dto) {
        return BillingHouseholdToEntity.toMonthlyEnergy(dto);
    }

    @Override
    protected BillingHouseholdId toId(MonthlyBillingEnergyHouseholdEntity entity) {
        return new BillingHouseholdId(entity.getMeterSerial(), entity.getEntryTimestamp());
    }

    @Override
    protected LocalDateTime stateAdvanceTo(LocalDateTime advanceTo) {
        return advanceTo.plusMonths(1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial, String profileObis, List<BillingEnergyHouseholdDTO> readings, CapturePeriod cp) {
        return super.saveBatchAndAdvanceCursor(meterSerial, profileObis, readings, cp, "MonthlyBillingEnergyHouseholdEntity");
    }
}

