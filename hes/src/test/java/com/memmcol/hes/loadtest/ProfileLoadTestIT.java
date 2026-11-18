package com.memmcol.hes.loadtest;

import com.memmcol.hes.HesApplication;
import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.domain.profile.MeterHealthGate;
import com.memmcol.hes.infrastructure.dlms.DlmsPartialDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsResponseDecoder;
import com.memmcol.hes.infrastructure.dlms.DlmsTimestampDecoder;
import com.memmcol.hes.jobs.DefaultJobInitializer;
import com.memmcol.hes.jobs.services.ProfileExecutionService;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import com.memmcol.hes.trackByTimestamp.MeterProfileStateRepository;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = HesApplication.class)
@ActiveProfiles("loadtest")
class ProfileLoadTestIT {

    private static final String SERIAL_PREFIX = "LOADTEST-HH-";
    private static final String MODEL = System.getProperty("loadtest.model", "LOADTEST-HH");
    private static final String PROFILE_OBIS = System.getProperty("loadtest.obis", "1.0.99.1.0.255");
    private static final int METERS = Integer.getInteger("loadtest.meters", 5000);
    private static final int DAYS = Integer.getInteger("loadtest.days", 3);
    private static final int LATENCY_MS = Integer.getInteger("loadtest.latency-ms", 50);
    private static final int FAILURE_PCT = Integer.getInteger("loadtest.failures-pct", 0);
    private static final boolean CLEANUP = Boolean.parseBoolean(System.getProperty("loadtest.cleanup", "true"));

    @MockitoBean
    private DefaultJobInitializer defaultJobInitializer;

    @Autowired
    private ProfileExecutionService profileExecutionService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LoadTestStubDlmsReader stubReader;

    @Autowired
    private MeterHealthGate meterHealthGate;

    @Autowired
    @Qualifier("meterReadAdaptiveExecutor")
    private ThreadPoolExecutor meterReadExecutor;

    @DynamicPropertySource
    static void loadTestProperties(DynamicPropertyRegistry registry) {
        registry.add("hes.profile.household.models", () -> MODEL);
    }

    @Test
    void channelOneHouseholdFullScanAtScale() {
        EmbeddedChannel sharedChannel = new EmbeddedChannel();
        try {
            UUID orgId = existingId("organizations");
            UUID manufacturerId = existingId("manufacturers");
            purgeFakeData();
            seedMetadata();
            seedMeters(orgId, manufacturerId);
            seedSmartMeterInfo(orgId);
            seedState();

            for (int i = 1; i <= METERS; i++) {
                MeterConnections.bind(sharedChannel, SERIAL_PREFIX + String.format("%06d", i));
            }

            long rowsBefore = countFakeRows();
            long readsBefore = stubReader.getReadCalls().get();
            long producedBefore = stubReader.getRowsProduced().get();
            long completedBefore = meterReadExecutor.getCompletedTaskCount();

            AtomicBoolean sampling = new AtomicBoolean(true);
            AtomicInteger peakPool = new AtomicInteger();
            AtomicInteger peakActive = new AtomicInteger();
            AtomicInteger peakQueue = new AtomicInteger();

            Thread sampler = new Thread(() -> {
                while (sampling.get()) {
                    peakPool.accumulateAndGet(meterReadExecutor.getPoolSize(), Math::max);
                    peakActive.accumulateAndGet(meterReadExecutor.getActiveCount(), Math::max);
                    peakQueue.accumulateAndGet(meterReadExecutor.getQueue().size(), Math::max);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
            sampler.setDaemon(true);
            sampler.start();

            long start = System.nanoTime();
            profileExecutionService.readChannelOneHouseholdForAll(PROFILE_OBIS);
            long elapsedNanos = System.nanoTime() - start;

            sampling.set(false);
            sampler.interrupt();

            long rowsAfter = countFakeRows();
            long inserted = rowsAfter - rowsBefore;
            long reads = stubReader.getReadCalls().get() - readsBefore;
            long produced = stubReader.getRowsProduced().get() - producedBefore;
            long failures = stubReader.getSimulatedFailures().get();
            long completed = meterReadExecutor.getCompletedTaskCount() - completedBefore;

            double wallSeconds = elapsedNanos / 1_000_000_000.0;

            System.out.println();
            System.out.println("================ HES LOAD TEST REPORT ================");
            System.out.printf("targetDb=%s%n", jdbc.queryForObject("SELECT current_database()", String.class));
            System.out.printf("meters=%d days=%d latencyMs=%d failurePct=%d rowIntervalMinutes=%d cores=%d%n",
                    METERS, DAYS, LATENCY_MS, FAILURE_PCT, stubReader.getRowIntervalMinutes(),
                    Runtime.getRuntime().availableProcessors());
            System.out.printf("wallTime=%.2fs  metersPerSec=%.1f%n", wallSeconds, METERS / wallSeconds);
            System.out.printf("stubReads=%d  stubRowsProduced=%d  stubFailures=%d%n", reads, produced, failures);
            System.out.printf("dbRowsInserted=%d  rowsPerSec=%.0f%n", inserted, inserted / wallSeconds);
            System.out.printf("executorPoolPeak=%d  activePeak=%d  queuePeak=%d  tasksCompleted=%d%n",
                    peakPool.get(), peakActive.get(), peakQueue.get(), completed);
            System.out.printf("quarantinedMeters=%d%n", meterHealthGate.quarantineCount());
            System.out.println("======================================================");
            System.out.println();

            assertTrue(inserted > 0, "expected profile rows to be inserted");
            if (FAILURE_PCT == 0) {
                assertTrue(inserted >= produced * 0.99, "expected nearly all produced rows to be persisted");
            }
        } finally {
            MeterConnections.remove(sharedChannel);
            sharedChannel.close();
            if (CLEANUP) {
                purgeFakeData();
            }
        }
    }

    private UUID existingId(String table) {
        UUID id = jdbc.queryForObject("SELECT id FROM " + table + " LIMIT 1", UUID.class);
        if (id == null) {
            throw new IllegalStateException("dev DB table '" + table + "' has no rows; load test needs an existing id for FK seeding");
        }
        return id;
    }

    private void purgeFakeData() {
        jdbc.update("DELETE FROM profile_channel_one_hh WHERE meter_serial LIKE ?", SERIAL_PREFIX + "%");
        jdbc.update("DELETE FROM smart_meter_info WHERE meter_id IN (SELECT id FROM meters WHERE meter_number LIKE ?)", SERIAL_PREFIX + "%");
        jdbc.update("DELETE FROM meters WHERE meter_number LIKE ?", SERIAL_PREFIX + "%");
        jdbc.update("DELETE FROM meter_profile_state WHERE meter_serial LIKE ?", SERIAL_PREFIX + "%");
        jdbc.update("DELETE FROM model_profile_metadata WHERE meter_model = ?", MODEL);
    }

    private void seedMetadata() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{MODEL, PROFILE_OBIS, LoadTestStubDlmsReader.CLOCK_OBIS, 8, 0, "entry_timestamp", "CLOCK"});
        int index = 1;
        for (Map.Entry<String, String> capture : LoadTestStubDlmsReader.ENERGY_CAPTURES.entrySet()) {
            rows.add(new Object[]{MODEL, PROFILE_OBIS, capture.getKey(), 3, index++, capture.getValue(), "NON_SCALER"});
        }
        jdbc.batchUpdate("""
                INSERT INTO model_profile_metadata
                    (meter_model, profile_obis, capture_obis, class_id, attribute_index, scaler, capture_index, column_name, multiply_by, type)
                VALUES (?, ?, ?, ?, 2, 1.0, ?, ?, 'CTPT', ?)
                """, rows);
    }

    private void seedMeters(UUID orgId, UUID manufacturerId) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(DAYS);
        jdbc.update("""
                INSERT INTO meters (
                    id, org_id, sim_number, meter_category, meter_class, meter_type, status,
                    created_at, updated_at, meter_number, type,
                    old_sgc, new_sgc, new_krn, old_krn, old_tariff_index, new_tariff_index, meter_manufacturer)
                SELECT gen_random_uuid(),
                       ?,
                       'LOADTEST-SIM',
                       'LOADTEST',
                       'HH',
                       'LOADTEST',
                       'ACTIVE',
                       ?,
                       now(),
                       ? || lpad(i::text, 6, '0'),
                       'LOADTEST',
                       '0', '0', '0', '0',
                       1, 1,
                       ?
                FROM generate_series(1, ?) AS i
                """, orgId, createdAt, SERIAL_PREFIX, manufacturerId, METERS);
    }

    private void seedSmartMeterInfo(UUID orgId) {
        jdbc.update("""
                INSERT INTO smart_meter_info (id, meter_id, org_id, meter_model, protocol, authentication, password)
                SELECT gen_random_uuid(), m.id, ?, ?, 'DLMS', 'LOW', '0'
                FROM meters m
                WHERE m.meter_number LIKE ?
                """, orgId, MODEL, SERIAL_PREFIX + "%");
    }

    private void seedState() {
        jdbc.update("""
                INSERT INTO meter_profile_state (meter_serial, profile_obis, last_timestamp, capture_period_sec, updated_at)
                SELECT m.meter_number, ?, NULL, 3600, now()
                FROM meters m
                WHERE m.meter_number LIKE ?
                """, PROFILE_OBIS, SERIAL_PREFIX + "%");
    }

    private long countFakeRows() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM profile_channel_one_hh WHERE meter_serial LIKE ?",
                Long.class, SERIAL_PREFIX + "%");
        return count == null ? 0L : count;
    }

    @TestConfiguration
    static class StubReaderConfiguration {

        @Bean
        @Primary
        LoadTestStubDlmsReader loadTestStubDlmsReader(SessionManagerMultiVendor sessionManager,
                                                      MeterLockPort meterLockPort,
                                                      DlmsPartialDecoder partialDecoder,
                                                      DlmsTimestampDecoder timestampDecoder,
                                                      TxRxService txRxService,
                                                      MeterProfileStateRepository stateRepository,
                                                      MeterRepository meterRepository,
                                                      DlmsResponseDecoder responseDecoder) {
            return new LoadTestStubDlmsReader(sessionManager, meterLockPort, partialDecoder, timestampDecoder,
                    txRxService, stateRepository, meterRepository, responseDecoder,
                    LATENCY_MS, FAILURE_PCT, Integer.getInteger("loadtest.row-interval-minutes", 60));
        }
    }
}
