package com.xgls.platform.v2.api.v1.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApiV1InfoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "api", "v1",
                "status", "ok",
                "note", "Legacy-compatible JSON shapes will live under /api/v1 when ported.");
    }
}
