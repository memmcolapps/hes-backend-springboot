package com.memmcol.hes.api.gridFlex;

import com.memmcol.hes.gridflex.dtos.RealtimeReadRequest;
import com.memmcol.hes.gridflex.services.RealtimeReadSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/realtime")
@CrossOrigin(origins = "*") // ✅ allow all origins (temporary for testing)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Realtime SSE Reading", description = "API for Gridflex Realtime response")
public class RealtimeReadController {

    private final RealtimeReadSseService realtimeReadSseService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Read instantaneous data")
    public SseEmitter streamRealtimeRead(@RequestBody RealtimeReadRequest request) {
        log.info("🔌 Starting real-time OBIS streaming for {} meters and {} OBIS",
                request.getMeters().size(), request.getObisString().size());

        return realtimeReadSseService.streamRealtimeRead(request);
    }
}
