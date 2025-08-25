package com.memmcol.hes.infrastructure.persistence;

import com.memmcol.hes.application.port.out.ProfileStatePort;
import com.memmcol.hes.domain.profile.ProfileRow;
import com.memmcol.hes.domain.profile.ProfileState;
import com.memmcol.hes.domain.profile.ProfileSyncResult;
import com.memmcol.hes.domain.profile.ProfileTimestamp;
import com.memmcol.hes.model.ProfileChannel2Reading;
import com.memmcol.hes.model.ProfileChannel2ReadingDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelTwoPersistAdapter {

    @PersistenceContext
    private final EntityManager em;
    private static final int FLUSH_BATCH = 100;
    private final ProfileStatePort statePort;

    @Transactional
    public ProfileSyncResult saveAll(List<ProfileChannel2ReadingDTO> filteredRows, String meterSerial, String obis){
        ProfileState st = statePort.loadState(meterSerial, obis); // or null if you dropped obis
        LocalDateTime previousLast = (st != null && st.lastTimestamp() != null)
                ? st.lastTimestamp().value()
                : null;

        if (filteredRows == null || filteredRows.isEmpty()) {
            log.info("saveBatchAndAdvanceCursor: no rows for meter={}", meterSerial);
            return new ProfileSyncResult(
                    0, 0, 0, previousLast, previousLast, previousLast, false
            );
        }

        int total = filteredRows.size();
        int inserted = doBatchInsertHibernate(filteredRows);
        int duplicate = total - inserted;

        LocalDateTime incomingMax = filteredRows.stream()
                .map(ProfileChannel2ReadingDTO::getEntryTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(previousLast);

        LocalDateTime advanceTo = previousLast == null
                ? incomingMax
                : (previousLast.isAfter(incomingMax) ? previousLast : incomingMax);

        boolean advanced = previousLast == null || advanceTo.isAfter(previousLast);

        log.info("Batch persisted meter={} total={} inserted={} dup={} start={} end={} advanceTo={}",
                meterSerial, total, inserted, duplicate, previousLast, incomingMax, advanceTo);

        return new ProfileSyncResult(total, inserted, duplicate, previousLast, incomingMax, advanceTo, advanced);
    }

    private int doBatchInsertHibernate(List<ProfileChannel2ReadingDTO> filteredRows) {
        int saved = 0;

        Session session = em.unwrap(Session.class);
        for (ProfileChannel2ReadingDTO r : filteredRows) {
            ProfileChannel2Reading entity = new ProfileChannel2Reading();
            entity.setMeterSerial(r.getMeterSerial());
            entity.setModelNumber(r.getModelNumber());
            entity.setEntryIndex(-1);
            entity.setEntryTimestamp(r.getEntryTimestamp());
            entity.setImportActiveEnergy(r.getImportActiveEnergy());
            entity.setExportActiveEnergy(r.getExportActiveEnergy());
            entity.setRawData(r.getRawData());
            entity.setReceivedAt(LocalDateTime.now());

            session.persist(entity);
            saved++;

            if (saved % FLUSH_BATCH == 0) {
                session.flush();
                session.clear();
            }

        }
        return saved;
    }
}
