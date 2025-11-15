package com.memmcol.hes.api.gridFlex;

//2️⃣ SSE Controller

import com.memmcol.hes.gridflex.sse.MeterStatusEvent;
import com.memmcol.hes.gridflex.sse.MeterStatusSsePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/realtime/meter-status")
@RequiredArgsConstructor
public class MeterStatusSseController {

    private final MeterStatusSsePublisher publisher;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<MeterStatusEvent>> streamMeterStatus() {

        return publisher.getStream()
                .map(event -> ServerSentEvent.<MeterStatusEvent>builder()
                        .event("meter-status")
                        .id(event.getMeterNo())
                        .data(event)
                        .build());
    }
}
