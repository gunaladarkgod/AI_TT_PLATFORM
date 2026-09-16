package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.ResearchCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/** 研究方向、基线与改进包的只读目录接口。 */
@Tag(name = "研究算法目录")
@RestController
@RequestMapping("/api/research")
public class ResearchCatalogController {

    private final ResearchCatalogService researchCatalogService;

    public ResearchCatalogController(ResearchCatalogService researchCatalogService) {
        this.researchCatalogService = researchCatalogService;
    }

    @GetMapping("/directions")
    public AjaxResult directions() {
        return AjaxResult.success(researchCatalogService.listDirections());
    }

    @GetMapping("/baselines")
    public AjaxResult baselines(@RequestParam String direction) {
        return AjaxResult.success(researchCatalogService.listBaselines(direction));
    }

    @GetMapping("/improvements")
    public AjaxResult improvements(@RequestParam String baselineId) {
        return AjaxResult.success(researchCatalogService.listImprovements(baselineId));
    }

    @GetMapping("/package/detail")
    public AjaxResult packageDetail(@RequestParam String id) {
        Map<String, Object> detail = researchCatalogService.detail(id);
        return detail == null ? AjaxResult.error("研究算法包不存在或算法 ID 格式错误") : AjaxResult.success(detail);
    }

    /** 在保存任务前解析改进包组合；只校验目录声明，不执行任何算法源码。 */
    @PostMapping("/stack/resolve")
    public AjaxResult resolveStack(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        String baselineId = String.valueOf(body.getOrDefault("baselineId", "")).trim();
        List<String> improvementIds = body.get("improvementIds") instanceof List<?> values
                ? values.stream().map(value -> String.valueOf(value).trim()).toList()
                : List.of();
        return AjaxResult.success(researchCatalogService.resolveStack(baselineId, improvementIds));
    }
}
