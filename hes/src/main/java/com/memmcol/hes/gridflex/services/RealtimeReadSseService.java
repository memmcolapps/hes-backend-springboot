package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.gridflex.dtos.MeterDto;
import com.memmcol.hes.gridflex.dtos.ObisDto;
import com.memmcol.hes.gridflex.dtos.RealtimeReadRequest;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hesTraining.services.MeterReadingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RealtimeReadSseService {
    private final MeterReadingService trainingService; // your OBIS reader
    private final MeterRepository meterRepository;
    private final ExecutorService streamExecutor;
    private final ExecutorService realtimeReadExecutor;
    private final int maxMetersPerRequest;
    private final int maxObisPerRequest;
    private final Duration requestTimeout;
    private final String fallbackMdModel;
    private final String fallbackNonMdModel;

    public RealtimeReadSseService(MeterReadingService trainingService,
                                  MeterRepository meterRepository,
                                  @Qualifier("realtimeStreamExecutor") ExecutorService streamExecutor,
                                  @Qualifier("meterReadExecutor") ExecutorService realtimeReadExecutor,
                                  @Value("${hes.realtime-read.max-meters:50}") int maxMetersPerRequest,
                                  @Value("${hes.realtime-read.max-obis:20}") int maxObisPerRequest,
                                  @Value("${hes.realtime-read.timeout-seconds:120}") long requestTimeoutSeconds,
                                  @Value("${hes.realtime-read.fallback-md-model:MDModelX}") String fallbackMdModel,
                                  @Value("${hes.realtime-read.fallback-non-md-model:NonMDModelX}") String fallbackNonMdModel) {
        this.trainingService = trainingService;
        this.meterRepository = meterRepository;
        this.streamExecutor = streamExecutor;
        this.realtimeReadExecutor = realtimeReadExecutor;
        this.maxMetersPerRequest = Math.max(1, maxMetersPerRequest);
        this.maxObisPerRequest = Math.max(1, maxObisPerRequest);
        this.requestTimeout = Duration.ofSeconds(Math.max(1, requestTimeoutSeconds));
        this.fallbackMdModel = fallbackMdModel;
        this.fallbackNonMdModel = fallbackNonMdModel;
    }

    public SseEmitter streamRealtimeRead(RealtimeReadRequest request) {
        RealtimeReadPlan plan = buildPlan(request);
        SseEmitter emitter = new SseEmitter(requestTimeout.plusSeconds(5).toMillis());

        streamExecutor.execute(() -> {
            try {
                send(emitter, "heartbeat", Map.of(
                        "status", "Connected",
                        "meters", plan.meters().size(),
                        "obis", plan.obisList().size()
                ));

                RealtimeReadSummary summary = executeRealtimeRead(plan, emitter);

                send(emitter, "completed", Map.of(
                        "statusmessage", "Realtime read completed.",
                        "meters", plan.meters().size(),
                        "obis", plan.obisList().size(),
                        "success", summary.success(),
                        "failed", summary.failed()
                ));

                emitter.complete();
            } catch (Exception e) {
                log.error("❌ Unexpected realtime read error: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private RealtimeReadPlan buildPlan(RealtimeReadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        List<String> meters = normalize(request.getMeters());
        List<String> obisStrings = normalize(request.getObisString());

        if (meters.isEmpty()) {
            throw new IllegalArgumentException("At least one meter is required.");
        }
        if (obisStrings.isEmpty()) {
            throw new IllegalArgumentException("At least one OBIS code is required.");
        }
        if (meters.size() > maxMetersPerRequest) {
            throw new IllegalArgumentException("Realtime read supports at most " + maxMetersPerRequest + " meters per request.");
        }
        if (obisStrings.size() > maxObisPerRequest) {
            throw new IllegalArgumentException("Realtime read supports at most " + maxObisPerRequest + " OBIS codes per request.");
        }

        boolean requestIsMd = "MD".equalsIgnoreCase(request.getMeterType());
        Map<String, MeterDTO> dbMeters = meterRepository.findMeterDetailsByMeterNumberIn(meters)
                .stream()
                .peek(MeterDTO::determineMD)
                .collect(Collectors.toMap(
                        MeterDTO::getMeterNumber,
                        dto -> dto,
                        (existing, replacement) -> existing
                ));

        List<MeterDto> meterList = new ArrayList<>(meters.size());
        for (String serial : meters) {
            MeterDTO dbMeter = dbMeters.get(serial);
            if (dbMeter != null) {
                meterList.add(new MeterDto(serial, dbMeter.getMeterModel(), dbMeter.isMD()));
            } else {
                meterList.add(new MeterDto(
                        serial,
                        requestIsMd ? fallbackMdModel : fallbackNonMdModel,
                        requestIsMd
                ));
            }
        }

        List<ObisDto> obisList = obisStrings.stream()
                .map(o -> new ObisDto(o, "DefaultGroup", "Description", 1.0, "kWh"))
                .toList();

        return new RealtimeReadPlan(meterList, obisList);
    }

    private List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private RealtimeReadSummary executeRealtimeRead(RealtimeReadPlan plan, SseEmitter emitter)
            throws InterruptedException, IOException {
        CompletionService<RealtimeReadSummary> completionService =
                new ExecutorCompletionService<>(realtimeReadExecutor);
        List<Future<RealtimeReadSummary>> submitted = new ArrayList<>(plan.meters().size());
        AtomicBoolean acceptingEvents = new AtomicBoolean(true);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (MeterDto meter : plan.meters()) {
            submitted.add(completionService.submit(() -> readMeterObisValues(
                    meter, plan.obisList(), emitter, acceptingEvents, success, failed)));
        }

        int completedMeters = 0;
        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();

        try {
            while (completedMeters < submitted.size()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }

                Future<RealtimeReadSummary> future = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (future == null) {
                    break;
                }

                completedMeters++;
                try {
                    future.get();
                } catch (ExecutionException ex) {
                    failed.addAndGet(plan.obisList().size());
                    send(emitter, "reading", errorPayload(null, null, ex.getMessage()));
                }
            }

            int timedOutMeters = submitted.size() - completedMeters;
            if (timedOutMeters > 0) {
                acceptingEvents.set(false);
                cancelPending(submitted);
                int expectedReadings = plan.meters().size() * plan.obisList().size();
                int pendingReadings = expectedReadings - success.get() - failed.get();
                if (pendingReadings > 0) {
                    failed.addAndGet(pendingReadings);
                }
                send(emitter, "warning", Map.of(
                        "statusmessage", "Realtime read timed out.",
                        "timedOutMeters", timedOutMeters
                ));
            }

            return new RealtimeReadSummary(success.get(), failed.get());
        } finally {
            acceptingEvents.set(false);
            cancelPending(submitted);
        }
    }

    private RealtimeReadSummary readMeterObisValues(MeterDto meter,
                                                    List<ObisDto> obisList,
                                                    SseEmitter emitter,
                                                    AtomicBoolean acceptingEvents,
                                                    AtomicInteger totalSuccess,
                                                    AtomicInteger totalFailed) throws IOException {
        long meterStarted = System.nanoTime();
        log.info("⚙️ Realtime read meter={}, obisCount={}", meter.getMeterSerial(), obisList.size());

        int success = 0;
        int failed = 0;
        for (ObisDto obis : obisList) {
            if (Thread.currentThread().isInterrupted() || !acceptingEvents.get()) {
                break;
            }

            Map<String, Object> reading = readSingleObis(meter, obis);
            if (Integer.valueOf(0).equals(reading.get("statuscode"))) {
                success++;
                totalSuccess.incrementAndGet();
            } else {
                failed++;
                totalFailed.incrementAndGet();
            }

            if (acceptingEvents.get()) {
                send(emitter, "reading", reading);
            }
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - meterStarted);
        log.info("✅ Realtime read meter={} finished success={} failed={} elapsedMs={}",
                meter.getMeterSerial(), success, failed, elapsedMs);
        return new RealtimeReadSummary(success, failed);
    }

    private Map<String, Object> readSingleObis(MeterDto meter, ObisDto obis) {
        Map<String, Object> responseData = basePayload(meter, obis.getObisString());
        long started = System.nanoTime();

        try {
            Object value;
            if (meter.isMD()) {
                value = trainingService.readObisValue_MDMeters(
                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
            } else {
                value = trainingService.readObisValue_NonMDMeters(
                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
            }

            String desc = mapObisCode(obis.getObisString());
            responseData.put("desc", desc);
            responseData.put("value", extractValue(value));
            responseData.put("statusmessage", "SUCCESS");
        } catch (Exception e) {
            log.error("❌ Error reading OBIS {} for meter {}: {}",
                    obis.getObisString(), meter.getMeterSerial(), e.getMessage());

            responseData.put("statuscode", -1);
            responseData.put("value", null);
            responseData.put("statusmessage", e.getMessage());
        } finally {
            responseData.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }

        return responseData;
    }

    private Object extractValue(Object value) {
        if (value instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            if (body instanceof Map<?, ?> mapValue) {
                if (mapValue.containsKey("Actual Value")) {
                    return mapValue.get("Actual Value");
                }
                if (mapValue.containsKey("error")) {
                    throw new IllegalStateException(String.valueOf(mapValue.get("error")));
                }
                return mapValue.toString();
            }
            return body != null ? body.toString() : null;
        }

        return value != null ? value.toString() : null;
    }

    private Map<String, Object> basePayload(MeterDto meter, String obisString) {
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("statuscode", 0);
        responseData.put("meterModel", meter.getMeterModel());
        responseData.put("meterNo", meter.getMeterSerial());
        responseData.put("obisString", obisString);
        responseData.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        return responseData;
    }

    private Map<String, Object> errorPayload(String meterNo, String obisString, String message) {
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("statuscode", -1);
        responseData.put("meterModel", null);
        responseData.put("meterNo", meterNo);
        responseData.put("obisString", obisString);
        responseData.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        responseData.put("value", null);
        responseData.put("statusmessage", message);
        return responseData;
    }

    private void send(SseEmitter emitter, String eventName, Object data) throws IOException {
        synchronized (emitter) {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        }
    }

    private void cancelPending(List<? extends Future<?>> futures) {
        futures.stream()
                .filter(future -> !future.isDone())
                .forEach(future -> future.cancel(true));
    }

    private record RealtimeReadPlan(List<MeterDto> meters, List<ObisDto> obisList) {
    }

    private record RealtimeReadSummary(int success, int failed) {
    }

    private String mapObisCode(String obisCode) {

        if (obisCode == null) return null;

        switch (obisCode) {

            //Energy
            case "3;1.0.1.8.0.255;2;0":
                return "Active energy Import (+A)";
            case "3;1.0.1.8.1.255;2;0":
                return "Active energy Import (+A) of public grid";
            case "3;1.0.1.8.2.255;2;0":
                return "Active energy Import (+A) of private grid";
            case "3;1.0.2.8.0.255;2;0":
                return "Active energy Export (-A)";
            case "3;1.0.2.8.1.255;2;0":
                return "Active energy Export (-A) of public grid";
            case "3;1.0.2.8.2.255;2;0":
                return "Active energy Export (-A) of private grid";

            case "3;1.0.3.8.0.255;2;0":
                return "Reactive energy Import (+A)";
            case "3;1.0.3.8.1.255;2;0":
                return "Reactive energy Import (+A) of public grid";
            case "3;1.0.3.8.2.255;2;0":
                return "Reactive energy Import (+A) of private grid";
            case "3;1.0.4.8.0.255;2;0":
                return "Reactive energy Export (-A)";
            case "3;1.0.4.8.1.255;2;0":
                return "Reactive energy Export (-A) of public grid";
            case "3;1.0.4.8.2.255;2;0":
                return "Reactive energy Export (-A) of private grid";

            //Demand
            case "4;1.0.1.6.0.255;2;0":
                return "Maximum Demand Register  - Active energy import (+A)";
            case "4;1.0.2.6.1.255;2;0":
                return "Maximum Demand Register  - Active energy export (-A)";
            case "4;1.0.2.6.2.255;2;0":
                return "Maximum Demand Register  - Active energy export (-A)";

            case "4;1.0.3.6.0.255;2;0":
                return "Maximum Demand Register  - Reactive energy import (+A)";
            case "4;1.0.3.6.1.255;2;0":
                return "Maximum Demand Register  - Reactive energy import (+A)";
            case "4;1.0.3.6.2.255;2;0":
                return "Maximum Demand Register  - Reactive energy import (+A)";
            case "4;1.0.4.6.0.255;2;0":
                return "Maximum Demand Register  - Reactive energy export (-A)";
            case "4;1.0.4.6.1.255;2;0":
                return "Maximum Demand Register  - Reactive energy export (-A)";
            case "4;1.0.4.6.2.255;2;0":
                return "Maximum Demand Register  - Reactive energy export (-A)";

            case "4;1.0.9.6.0.255;2;0":
                return "Maximum Demand Register  - Apparent energy import (+A)";
            case "4;1.0.9.6.1.255;2;0":
                return "Maximum Demand Register  - Apparent energy import (+A)";
            case "4;1.0.9.6.2.255;2;0":
                return "Maximum Demand Register  - Apparent energy import (+A) ";
            case "4;1.0.10.6.0.255;2;0":
                return "Maximum Demand Register  - Apparent energy export (-A)";
            case "4;1.0.10.6.1.255;2;0":
                return "Maximum Demand Register  - Apparent energy export (-A)";
            case "4;1.0.10.6.2.255;2;0":
                return "Maximum Demand Register  - Apparent energy export (-A)";

            case "4;1.0.1.6.0.255;5;0":
                return "Maximum Demand Register  - Active energy import (+A) occurrence time";
            case "4;1.0.2.6.1.255;5;0":
                return "Maximum Demand Register  - Active energy export (-A) occurrence time of public grid";
            case "4;1.0.2.6.2.255;5;0":
                return "Maximum Demand Register  - Active energy export (-A) occurrence time of private grid";


            case "4;1.0.3.6.0.255;5;0":
                return "Maximum Demand Register  - Reactive energy import (+A) occurrence time";
            case "4;1.0.3.6.1.255;5;0":
                return "Maximum Demand Register  - Reactive energy import (+A) occurrence time of public grid";
            case "4;1.0.3.6.2.255;5;0":
                return "Maximum Demand Register  - Reactive energy import (+A) occurrence time of private grid";
            case "4;1.0.4.6.0.255;5;0":
                return "Maximum Demand Register  - Reactive energy export (-A) occurrence time";
            case "4;1.0.4.6.1.255;5;0":
                return "Maximum Demand Register  - Reactive energy export (-A) occurrence time of public grid";
            case "4;1.0.4.6.2.255;5;0":
                return "Maximum Demand Register  - Reactive energy export (-A) occurrence time of private grid";

            case "4;1.0.9.6.0.255;5;0":
                return "Maximum Demand Register  - Apparent energy import (+A) occurrence time";
            case "4;1.0.9.6.1.255;5;0":
                return "Maximum Demand Register  - Apparent energy import (+A) occurrence time of public grid";
            case "4;1.0.9.6.2.255;5;0":
                return "Maximum Demand Register  - Apparent energy import (+A) occurrence time of private grid";
            case "4;1.0.10.6.0.255;5;0":
                return "Maximum Demand Register  - Apparent energy export (-A) occurrence time";
            case "4;1.0.10.6.1.255;5;0":
                return "Maximum Demand Register  - Apparent energy export (-A) occurrence time of public grid";
            case "4;1.0.10.6.2.255;5;0":
                return "Maximum Demand Register  - Apparent energy export (-A) occurrence time of private grid";

            //instantaneous
            case "3;1.0:32.7.0.255;2;0":
                return "Voltage in phase L1";
            case "3;1.0:52.7.0.255;2;0":
                return "Voltage in phase L2";
            case "3;1.0:72.7.0.255;2;0":
                return "Voltage in phase L3";
            case "3;1.0:31.7.0.255;2;0":
                return "Current in phase L1";
            case "3;1.0:51.7.0.255;2;0":
                return "Current in phase L2";
            case "3;1.0:71.7.0.255;2;0":
                return "Current in phase L3";

            case "3;1.0:91.7.0.255;2;0":
                return "Neutral Current";
            case "3;1.0:14.7.0.255;2;0":
                return "Frequency";
            case "3;1.0:1.7.0.255;2;0":
                return "Total active power";
            case "3;1.0:21.7.0.255;2;0":
                return "Active power in phase L1";
            case "3;1.0:41.7.0.255;2;0":
                return "Active power in phase L2";
            case "3;1.0:61.7.0.255;2;0":
                return "Active power in phase L3";

            case "3;1.0:3.7.0.255;2;0":
                return "Total reactive power";
            case "3;1.0:23.7.0.255;2;0":
                return "Reactive power in phase L1";
            case "3;1.0:43.7.0.255;2;0":
                return "Reactive power in phase L2";
            case "3;1.0:63.7.0.255;2;0":
                return "Reactive power in phase L3";
            case "3;1.0:9.7.0.255;2;0":
                return "Total apparent power";
            case "3;1.0:29.7.0.255;2;0":
                return "Apparent power in phase L1";
            case "3;1.0:49.7.0.255;2;0":
                return "Apparent power in phase L2";
            case "3;1.0:69.7.0.255;2;0":
                return "Apparent power in phase L3";
            case "3;1.0:13.7.0.255;2;0":
                return "Total power factor";
            case "3;1.0:33.7.0.255;2;0":
                return "Power factor in phase L1";
            case "3;1.0:53.7.0.255;2;0":
                return "Power factor in phase L2";
            case "3;1.0:73.7.0.255;2;0":
                return "Power factor in phase L3";

            // Clock
            case "8;0.0:1.0.0.255;2;0":
                return "Date and time";

//            // Disconnect control
//            case "70;0.0:96.3.10.255;2;0":
//                return "Output status";
//            case "70;0.0:96.3.10.255;4;0":
//                return "Output status";
//            case "70;0.0:96.3.10.255;1;0":
//                return "Disconnect the relay";
//            case "70;0.0:96.3.10.255;2;0":
//                return "Connect the relay";

            //Momas requirement
            case "3;1.0:140.129.0.255;2;0":
                return "Public Grid Credit";
            case "3;1.1:140.129.0.255;2;0":
                return "Private Grid Credit";
            case "1;0.0:97.97.0.255;2;0":
                return "Tariff index";
            case "1;1.0:120.129.0.255;2;0":
                return "Public Grid SGC Code";
            case "1;1.1:120.129.0.255;2;0":
                return "Private Grid SGC Code";
            case "3;1.0:140.129.2.255;2;0":
                return "Public Grid Cumulative power purchase credit [kWh]";
            case "3;1.1:140.129.2.255;2;0":
                return "Private Grid Cumulative power purchase credit [kWh]";
            case "3;1.0:1.9.0.255;2;0":
                return "Current month consumed credit [kWh]";

            case "1;1.0:134.129.2.255;2;0":
                return " Maximum vend limit [kWh]";
            case "1;1.0:129.129.2.255;2;0":
                return "Write TOKEN Code";

            default:
                return null;
        }
    }
}
