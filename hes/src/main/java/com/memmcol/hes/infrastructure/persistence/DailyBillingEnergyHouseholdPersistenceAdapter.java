package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.dto.BillingEnergyHouseholdDTO;
import com.memmcol.hes.entities.BillingHouseholdId;
import com.memmcol.hes.entities.BillingHouseholdToEntity;
import com.memmcol.hes.entities.DailyBillingEnergyHouseholdEntity;
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
public class DailyBillingEnergyHouseholdPersistenceAdapter extends AbstractBillingHouseholdPersistenceAdapter<BillingEnergyHouseholdDTO, DailyBillingEnergyHouseholdEntity, BillingHouseholdId> {
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
    protected Class<DailyBillingEnergyHouseholdEntity> entityClass() {
        return DailyBillingEnergyHouseholdEntity.class;
    }

    @Override
    protected String baseTableName() {
        return "daily_billing_energy_hh";
    }

    @Override
    protected String partitionPrefix() {
        return "daily_billing_energy_hh";
    }

    @Override
    protected LocalDateTime entryTimestamp(BillingEnergyHouseholdDTO dto) {
        return dto.getEntryTimestamp();
    }

    @Override
    protected DailyBillingEnergyHouseholdEntity toEntity(BillingEnergyHouseholdDTO dto) {
        return BillingHouseholdToEntity.toDailyEnergy(dto);
    }

    @Override
    protected BillingHouseholdId toId(DailyBillingEnergyHouseholdEntity entity) {
        return new BillingHouseholdId(entity.getMeterSerial(), entity.getEntryTimestamp());
    }

    @Override
    protected LocalDateTime stateAdvanceTo(LocalDateTime advanceTo) {
        return advanceTo.plusDays(1);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult saveBatchAndAdvanceCursor(String meterSerial, String profileObis, List<BillingEnergyHouseholdDTO> readings, CapturePeriod cp) {
        return super.saveBatchAndAdvanceCursor(meterSerial, profileObis, readings, cp, "DailyBillingEnergyHouseholdEntity");
    }
}

