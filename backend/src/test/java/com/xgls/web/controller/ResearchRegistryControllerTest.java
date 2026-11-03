package com.xgls.web.controller;

import com.xgls.web.service.ResearchRegistryService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResearchRegistryControllerTest {
    @Test
    void returnsErrorWithValidationDetails() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", false);
        payload.put("errors", List.of("文件错误"));
        ResearchRegistryService service = new ResearchRegistryService() {
            @Override
            public Map<String, Object> load() { return payload; }
        };
        var response = new ResearchRegistryController(service).get();
        assertFalse(response.isSuccess());
        assertSame(payload, response.getData());
    }
}
