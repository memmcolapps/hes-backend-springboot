package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.CapturePeriod;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.trackByTimestamp.MeterProfileState;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.3 State Adapter (JPA)
 */

@Component
@RequiredArgsConstructor
public class JpaProfileStateAdapter implements ProfileStatePort {

    private final EntityManager em;

    @Override
    public ProfileState loadState(String meterSerial, String profileObis) {
        MeterProfileState e = em.createQuery("""
                select s from MeterProfileState s
                 where s.meterSerial=:m and s.profileObis=:p
                """, MeterProfileState.class)
                .setParameter("m", meterSerial)
                .setParameter("p", profileObis)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (e == null) return null;
        return new ProfileState(
                e.getMeterSerial(),
                e.getProfileObis(),
                e.getLastTimestamp() == null ? null : new ProfileTimestamp(e.getLastTimestamp()),
                e.getCapturePeriodSec() == null ? null : new CapturePeriod(e.getCapturePeriodSec())
        );
    }

    @Override
    @Transactional
    public void upsertState(String meterSerial, String profileObis,
                            ProfileTimestamp lastTs, CapturePeriod capturePeriod) {

        MeterProfileState e = em.createQuery("""
                select s from MeterProfileState s
                 where s.meterSerial=:m and s.profileObis=:p
                """, MeterProfileState.class)
                .setParameter("m", meterSerial)
                .setParameter("p", profileObis)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (e == null) {
            e = new MeterProfileState();
            e.setMeterSerial(meterSerial);
            e.setProfileObis(profileObis);
        }
        if (lastTs != null) e.setLastTimestamp(lastTs.value());
        if (capturePeriod != null) e.setCapturePeriodSec(capturePeriod.seconds());
        e.setUpdatedAt(java.time.LocalDateTime.now());

        if (e.getId() == null) em.persist(e);
        else em.merge(e);
    }
}
