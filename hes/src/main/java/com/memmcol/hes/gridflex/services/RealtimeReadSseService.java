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
        SseEmitter emitter = new SseEmitter(requestTimeout.toMillis());

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
        CompletionService<List<Map<String, Object>>> completionService =
                new ExecutorCompletionService<>(realtimeReadExecutor);
        List<Future<List<Map<String, Object>>>> submitted = new ArrayList<>(plan.meters().size());

        for (MeterDto meter : plan.meters()) {
            submitted.add(completionService.submit(() -> readMeterObisValues(meter, plan.obisList())));
        }

        int completedMeters = 0;
        int success = 0;
        int failed = 0;
        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();

        try {
            while (completedMeters < submitted.size()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }

                Future<List<Map<String, Object>>> future = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (future == null) {
                    break;
                }

                completedMeters++;
                try {
                    List<Map<String, Object>> readings = future.get();
                    for (Map<String, Object> reading : readings) {
                        if (Integer.valueOf(0).equals(reading.get("statuscode"))) {
                            success++;
                        } else {
                            failed++;
                        }
                        send(emitter, "reading", reading);
                    }
                } catch (ExecutionException ex) {
                    failed += plan.obisList().size();
                    send(emitter, "reading", errorPayload(null, null, ex.getMessage()));
                }
            }

            int timedOutMeters = submitted.size() - completedMeters;
            if (timedOutMeters > 0) {
                cancelPending(submitted);
                failed += timedOutMeters * plan.obisList().size();
                send(emitter, "warning", Map.of(
                        "statusmessage", "Realtime read timed out.",
                        "timedOutMeters", timedOutMeters
                ));
            }

            return new RealtimeReadSummary(success, failed);
        } finally {
            cancelPending(submitted);
        }
    }

    private List<Map<String, Object>> readMeterObisValues(MeterDto meter, List<ObisDto> obisList) {
        log.info("⚙️ Realtime read meter={}, obisCount={}", meter.getMeterSerial(), obisList.size());

        List<Map<String, Object>> readings = new ArrayList<>(obisList.size());
        for (ObisDto obis : obisList) {
            readings.add(readSingleObis(meter, obis));
        }
        return readings;
    }

    private Map<String, Object> readSingleObis(MeterDto meter, ObisDto obis) {
        Map<String, Object> responseData = basePayload(meter, obis.getObisString());

        try {
            Object value;
            if (meter.isMD()) {
                value = trainingService.readObisValue_MDMeters(
                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
            } else {
                value = trainingService.readObisValue_NonMDMeters(
                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
            }

            responseData.put("value", extractValue(value));
            responseData.put("statusmessage", "SUCCESS");
        } catch (Exception e) {
            log.error("❌ Error reading OBIS {} for meter {}: {}",
                    obis.getObisString(), meter.getMeterSerial(), e.getMessage());

            responseData.put("statuscode", -1);
            responseData.put("value", null);
            responseData.put("statusmessage", e.getMessage());
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
}
