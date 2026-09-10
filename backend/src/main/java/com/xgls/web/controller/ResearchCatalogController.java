package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.ResearchCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
}
