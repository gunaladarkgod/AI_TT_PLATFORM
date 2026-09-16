package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.engine.EngineAdapterRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "训练引擎")
@RestController
@RequestMapping("/api/engines")
public class EngineAdapterController {
    private final EngineAdapterRegistry registry;
    public EngineAdapterController(EngineAdapterRegistry registry) { this.registry = registry; }
    @GetMapping
    public AjaxResult list() { return AjaxResult.success(registry.listAvailability()); }
}
