package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.ResearchRegistryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Read-only API for the repository-owned research registry. */
@Tag(name = "研究基线公约")
@RestController
@RequestMapping("/api/research-registry")
public class ResearchRegistryController {
    private final ResearchRegistryService service;

    public ResearchRegistryController(ResearchRegistryService service) { this.service = service; }

    @GetMapping
    public AjaxResult get() {
        Map<String, Object> result = service.load();
        return Boolean.TRUE.equals(result.get("valid"))
                ? AjaxResult.success(result)
                : AjaxResult.error("研究基线登记无效，请检查登记文件", result);
    }
}
