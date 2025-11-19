package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.gridflex.dtos.MeterDto;
import com.memmcol.hes.gridflex.dtos.ObisDto;
import com.memmcol.hes.gridflex.dtos.RealtimeReadRequest;
import com.memmcol.hesTraining.services.MeterReadingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeReadSseService {
    private final MeterReadingService trainingService; // your OBIS reader

    public SseEmitter streamRealtimeRead(RealtimeReadRequest request) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                // 1️⃣ Send initial "Connected" heartbeat immediately
                emitter.send(SseEmitter.event().name("heartbeat").data("Connected"));

                // 3️⃣ Process OBIS readings
                List<MeterDto> meterList = request.getMeters().stream()
                        .map(m -> new MeterDto(m, "MDModelX", "MD".equalsIgnoreCase(request.getMeterType())))
                        .toList();

                List<ObisDto> obisList = request.getObisString().stream()
                        .map(o -> new ObisDto(o, "DefaultGroup", "Description", 1.0, "kWh"))
                        .toList();

                for (MeterDto meter : meterList) {
                    log.info("⚙️ Reading meter: {}", meter.getMeterSerial());

                    for (ObisDto obis : obisList) {
                        Map<String, Object> responseData = new LinkedHashMap<>();
                        responseData.put("statuscode", 0);
                        responseData.put("meterModel", meter.getMeterModel());
                        responseData.put("meterNo", meter.getMeterSerial());
                        responseData.put("obisString", obis.getObisString());
                        responseData.put("timestamp", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));

                        try {
                            Object value;
                            if (meter.isMD()) {
                                value = trainingService.readObisValue_MDMeters(
                                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
                            } else {
                                value = trainingService.readObisValue_NonMDMeters(
                                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
                            }

                            // Extract Actual Value and error message only
                            if (value instanceof ResponseEntity<?> responseEntity) {
                                // Extract the body map
                                Object body = responseEntity.getBody();
                                if (body instanceof Map<?, ?> mapValue) {
                                    // 1️⃣ Success case: Actual Value exists
                                    if (mapValue.containsKey("Actual Value")) {
                                        responseData.put("value", mapValue.get("Actual Value"));
                                    }
                                    // 2️⃣ Error case: map contains "error"
                                    else if (mapValue.containsKey("error")) {
                                        String errorMessage = mapValue.get("error").toString();
                                        throw new RuntimeException(errorMessage);
                                    }
                                    // 3️⃣ Else: unexpected body
                                    else {
                                        responseData.put("value", mapValue.toString());
                                    }
                                } else {
                                    // Body is not a map → return raw
                                    responseData.put("value", body != null ? body.toString() : null);
                                }

                            } else {
                                // raw value is not ResponseEntity
                                responseData.put("value", value != null ? value.toString() : null);
                            }

                            responseData.put("statusmessage", "SUCCESS");

                        } catch (Exception e) {
                            log.error("❌ Error reading OBIS {} for meter {}: {}",
                                    obis.getObisString(), meter.getMeterSerial(), e.getMessage());

                            responseData.put("statuscode", -1);
                            responseData.put("value", null);
                            responseData.put("statusmessage", e.getMessage());
                        }

                        // Send SSE event
                        emitter.send(SseEmitter.event()
                                .name("reading")
                                .data(responseData));
                    }
                }

                // 4️⃣ Send completion event
                emitter.send(SseEmitter.event()
                        .name("completed")
                        .data("All meters and OBIS reads completed successfully."));

                // 5️⃣ Complete emitter and shutdown heartbeat
                emitter.complete();
            } catch (Exception e) {
                log.error("❌ Unexpected error: {}", e.getMessage());
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });

        return emitter;
    }

}
