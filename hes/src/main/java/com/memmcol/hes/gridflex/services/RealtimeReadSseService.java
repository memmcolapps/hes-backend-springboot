package com.memmcol.hes.gridflex.services;

import com.memmcol.hes.gridflex.dtos.MeterDto;
import com.memmcol.hes.gridflex.dtos.ObisDto;
import com.memmcol.hes.gridflex.dtos.RealtimeReadRequest;
import com.memmcol.hesTraining.services.MeterReadingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.time.ZonedDateTime;
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
                List<MeterDto> meterList = request.getMeters().stream()
                        .map(m -> new MeterDto(m,
                                "MDModelX",
                                "MD".equalsIgnoreCase(request.getMeterType())))
                        .toList();

                List<ObisDto> obisList = request.getObisString().stream()
                        .map(o -> new ObisDto(o,
                                "DefaultGroup",
                                "Description",
                                1.0,
                                "kWh"))
                        .toList();

                for (MeterDto meter : meterList) {
                    log.info("⚙️ Reading meter: {}", meter.getMeterSerial());

                    for (ObisDto obis : obisList) {
                        try {
                            Object value;
                            if (meter.isMD()) {
                                value = trainingService.readObisValue_MDMeters(
                                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
                            } else {
                                value = trainingService.readObisValue_NonMDMeters(
                                        meter.getMeterModel(), meter.getMeterSerial(), obis.getObisString());
                            }

                            Map<String, Object> data = Map.of(
                                    "statuscode", 0,
                                    "statusmessage", "SUCCESS",
                                    "meterNo", meter.getMeterSerial(),
                                    "meterModel", meter.getMeterModel(),
                                    "obisString", obis.getObisString(),
                                    "value", value + " " + obis.getUnit(),
                                    "timestamp", ZonedDateTime.now(ZoneId.systemDefault()).toString()
                            );

                            emitter.send(SseEmitter.event()
                                    .name("reading")
                                    .data(data));

                        } catch (Exception e) {
                            log.error("❌ Error reading OBIS {} for meter {}: {}",
                                    obis.getObisString(), meter.getMeterSerial(), e.getMessage());

                            Map<String, Object> errorData = Map.of(
                                    "statuscode", -1,
                                    "statusmessage", "FAILED: " + e.getMessage(),
                                    "meterNo", meter.getMeterSerial(),
                                    "obisString", obis.getObisString(),
                                    "value", null
                            );

                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(errorData));
                        }
                    }
                }

                emitter.send(SseEmitter.event()
                        .name("completed")
                        .data("All meters and OBIS reads completed successfully."));
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
