package com.xgls.web.secontroller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/sse")
@Tag(name = "数据集导入SSE")
public class SseController {
    private final CustomEventListener eventListener;

    public SseController(CustomEventListener eventListener) {
        this.eventListener = eventListener;
    }

    @GetMapping(path = "/export", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamEvents() {
        return eventListener.getEvents();
    }
}
