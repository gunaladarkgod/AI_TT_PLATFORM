package com.xgls.web.controller;

import com.xgls.web.common.preprocess_Result;
import com.xgls.web.entity.InstanceDatasetinfo;
import com.xgls.web.service.PreprocessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/preprocess")
public class PreprocessController {

    @Autowired
    private PreprocessService preprocessService;

    /**
     * 执行预处理：自动命名，支持多源数据集
     */
    @PostMapping("/run")
    public preprocess_Result<?> runPreprocess(@RequestBody PreprocessRequest request) {
        try {
            List<InstanceDatasetinfo> results = preprocessService.runPreprocess(
                    request.getSourceInstanceIds(),     // 源实例数据集ID列表
                    request.getEnhanceScriptId(),
                    request.getEnhanceParams(),
                    request.getAugmentScriptId(),
                    request.getAugmentParams()
            );
            return preprocess_Result.success(results);
        } catch (Exception e) {
            e.printStackTrace();
            return preprocess_Result.error("预处理执行失败：" + e.getMessage());
        }
    }

    // DTO：请求体结构（不再包含 outputNamePrefix）
    public static class PreprocessRequest {
        private List<Long> sourceInstanceIds;           // 必传：源数据集ID列表
        private Integer enhanceScriptId;
        private Map<String, Object> enhanceParams;
        private Integer augmentScriptId;
        private Map<String, Object> augmentParams;

        // Getters & Setters
        public List<Long> getSourceInstanceIds() {
            return sourceInstanceIds;
        }

        public void setSourceInstanceIds(List<Long> sourceInstanceIds) {
            this.sourceInstanceIds = sourceInstanceIds;
        }

        public Integer getEnhanceScriptId() {
            return enhanceScriptId;
        }

        public void setEnhanceScriptId(Integer enhanceScriptId) {
            this.enhanceScriptId = enhanceScriptId;
        }

        public Map<String, Object> getEnhanceParams() {
            return enhanceParams;
        }

        public void setEnhanceParams(Map<String, Object> enhanceParams) {
            this.enhanceParams = enhanceParams;
        }

        public Integer getAugmentScriptId() {
            return augmentScriptId;
        }

        public void setAugmentScriptId(Integer augmentScriptId) {
            this.augmentScriptId = augmentScriptId;
        }

        public Map<String, Object> getAugmentParams() {
            return augmentParams;
        }

        public void setAugmentParams(Map<String, Object> augmentParams) {
            this.augmentParams = augmentParams;
        }
    }
}