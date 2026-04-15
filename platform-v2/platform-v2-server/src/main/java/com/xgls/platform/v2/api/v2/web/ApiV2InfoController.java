package com.xgls.platform.v2.api.v2.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
public class ApiV2InfoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "api", "v2",
                "status", "ok",
                "note", "New canonical REST models + problem+json errors will be defined here.");
    }
}
