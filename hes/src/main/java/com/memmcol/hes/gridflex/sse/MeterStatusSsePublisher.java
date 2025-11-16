package com.memmcol.hes.gridflex.sse;

//1️⃣ Create a Global Event Sink
//1. SSE Publisher (Simple & reusable)

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MeterStatusSsePublisher {
    private final Map<String, SseEmitter> clients = new ConcurrentHashMap<>();

    //Option 1
    private final Sinks.Many<MeterStatusEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    public void publish(MeterStatusEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<MeterStatusEvent> getStream() {
        return sink.asFlux();
    }

    //Option 2

    public SseEmitter subscribe1(String clientId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        clients.put(clientId, emitter);

        emitter.onCompletion(() -> clients.remove(clientId));
        emitter.onTimeout(() -> clients.remove(clientId));
        emitter.onError((e) -> clients.remove(clientId));

        return emitter;
    }

    public void publish1(MeterStatusEvent event) {
        clients.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("meter-status")
                        .data(event));
            } catch (Exception ex) {
                log.warn("Removing dead SSE client: {}", id);
                clients.remove(id);
            }
        });
    }
}
