package com.xgls.web.secontroller;

import org.springframework.context.event.EventListener;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
class CustomEventListener {
    private final Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().directBestEffort();

    @EventListener
    public void handleCustomEvent(CustomEvent event) {
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .data(event.getMessage())
                .build());
    }

    public Flux<ServerSentEvent<String>> getEvents() {
        return sink.asFlux()
                .doOnError(e -> log.warn("sse err:{}", e.getMessage()))
                .doOnCancel(() -> log.warn("sse cancle"));
    }
}
