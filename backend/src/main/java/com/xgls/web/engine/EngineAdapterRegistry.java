package com.xgls.web.engine;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EngineAdapterRegistry {
    private final Map<String, TrainingEngineAdapter> adapters = new LinkedHashMap<>();

    public EngineAdapterRegistry(List<TrainingEngineAdapter> adapters) {
        adapters.forEach(adapter -> this.adapters.put(adapter.engineId(), adapter));
    }

    public TrainingEngineAdapter require(String engineId) {
        TrainingEngineAdapter adapter = adapters.get(engineId == null ? "" : engineId.trim().toLowerCase());
        if (adapter == null) throw new IllegalArgumentException("不支持的训练引擎：" + engineId);
        return adapter;
    }

    public List<Map<String, Object>> listAvailability() {
        return adapters.values().stream().map(adapter -> {
            Map<String, Object> value = new LinkedHashMap<>(adapter.availability());
            value.put("id", adapter.engineId());
            value.put("dataFormats", adapter.supportedDataFormats());
            return value;
        }).toList();
    }
}
