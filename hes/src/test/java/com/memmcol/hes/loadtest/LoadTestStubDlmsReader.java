package com.memmcol.hes.loadtest;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.application.port.out.ProfileReadException;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.domain.profile.ProfileMetadataResult;
import com.memmcol.hes.domain.profile.ProfileRowGeneric;
import com.memmcol.hes.infrastructure.dlms.DlmsPartialDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.infrastructure.dlms.DlmsResponseDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.trackByTimestamp.MeterProfileStateRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTestStubDlmsReader extends DlmsReaderUtils {

    public static final String CLOCK_OBIS = "0.0.1.0.0.255";
    public static final Map<String, String> ENERGY_CAPTURES;

    static {
        Map<String, String> captures = new LinkedHashMap<>();
        captures.put("1.0.1.8.0.255", "active_energy_import");
        captures.put("1.0.1.8.1.255", "active_energy_import_ongrid");
        captures.put("1.0.1.8.2.255", "active_energy_import_offgrid");
        captures.put("1.0.2.8.0.255", "active_energy_export");
        captures.put("1.0.2.8.1.255", "active_energy_export_ongrid");
        captures.put("1.0.2.8.2.255", "active_energy_export_offgrid");
        ENERGY_CAPTURES = Collections.unmodifiableMap(captures);
    }

    private final int latencyMillis;
    private final int failurePercent;
    private final int rowIntervalMinutes;

    private final AtomicLong readCalls = new AtomicLong();
    private final AtomicLong rowsProduced = new AtomicLong();
    private final AtomicLong simulatedFailures = new AtomicLong();

    public LoadTestStubDlmsReader(SessionManagerMultiVendor sessionManager,
                                  MeterLockPort meterLockPort,
                                  DlmsPartialDecoder partialDecoder,
                                  DlmsTimestampDecoder timestampDecoder,
                                  TxRxService txRxService,
                                  MeterProfileStateRepository meterProfileStateRepository,
                                  MeterRepository meterRepository,
                                  DlmsResponseDecoder responseDecoder,
                                  int latencyMillis,
                                  int failurePercent,
                                  int rowIntervalMinutes) {
        super(sessionManager, meterLockPort, partialDecoder, timestampDecoder, txRxService,
                meterProfileStateRepository, meterRepository, responseDecoder);
        this.latencyMillis = latencyMillis;
        this.failurePercent = failurePercent;
        this.rowIntervalMinutes = Math.max(1, rowIntervalMinutes);
    }

    @Override
    public List<ProfileRowGeneric> readRange(String model,
                                             String meterSerial,
                                             String profileObis,
                                             ProfileMetadataResult metadataResult,
                                             LocalDateTime from,
                                             LocalDateTime to,
                                             boolean mdMeter) throws Exception {
        readCalls.incrementAndGet();

        if (failurePercent > 0 && ThreadLocalRandom.current().nextInt(100) < failurePercent) {
            simulatedFailures.incrementAndGet();
            throw new Exception("loadtest simulated read failure meter=" + meterSerial);
        }

        if (latencyMillis > 0) {
            try {
                Thread.sleep(latencyMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }

        List<ProfileRowGeneric> rows = new ArrayList<>();
        LocalDateTime firstAllowed = from.plusMinutes(1);
        LocalDateTime cursor = firstAllowed.truncatedTo(ChronoUnit.HOURS);
        if (cursor.isBefore(firstAllowed)) {
            cursor = cursor.plusHours(1);
        }

        double base = Math.abs(meterSerial.hashCode() % 100000) / 10.0;
        long index = 0;

        while (cursor.isBefore(to)) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(CLOCK_OBIS, cursor);
            int offset = 0;
            for (Map.Entry<String, String> capture : ENERGY_CAPTURES.entrySet()) {
                values.put(capture.getKey(), base + index * 0.01 + offset * 0.001);
                offset++;
            }
            rows.add(new ProfileRowGeneric(
                    cursor.atZone(ZoneId.systemDefault()).toInstant(),
                    meterSerial,
                    profileObis,
                    values));
            index++;
            cursor = cursor.plusMinutes(rowIntervalMinutes);
        }

        rowsProduced.addAndGet(rows.size());
        return rows;
    }

    @Override
    public List<ProfileRowGeneric> recoverPartial(String meterSerial,
                                                  String profileObis,
                                                  String model,
                                                  ProfileMetadataResult metadataResult) throws ProfileReadException {
        throw new ProfileReadException("loadtest simulated recovery failure meter=" + meterSerial);
    }

    public AtomicLong getReadCalls() {
        return readCalls;
    }

    public AtomicLong getRowsProduced() {
        return rowsProduced;
    }

    public AtomicLong getSimulatedFailures() {
        return simulatedFailures;
    }

    public int getRowIntervalMinutes() {
        return rowIntervalMinutes;
    }
}
