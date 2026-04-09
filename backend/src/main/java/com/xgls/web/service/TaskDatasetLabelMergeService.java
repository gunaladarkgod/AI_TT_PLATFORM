package com.xgls.web.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.xgls.web.vo.dataset.TaskDatasetLabelMappingVO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 标签合并核心逻辑
 */
@Service
@Slf4j
public class TaskDatasetLabelMergeService {

    /**
     * 执行标签合并
     *
     * @param annotations          原始标注列表
     * @param mappings             标签映射列表（源 -> 目标）
     * @param allowedTargetLabels  目标标签白名单（可为空，为空时不做校验）
     * @return 合并结果
     */
    public LabelMergeResult mergeLabels(List<AnnotationRecord> annotations,
                                        List<TaskDatasetLabelMappingVO> mappings,
                                        Set<String> allowedTargetLabels) {
        return mergeLabelsWithFilter(annotations, mappings);
    }

    public LabelMergeResult mergeLabelsWithFilter(List<AnnotationRecord> annotations,
                                                  List<TaskDatasetLabelMappingVO> mappings) {
        if (CollUtil.isEmpty(annotations)) {
            return LabelMergeResult.empty();
        }

        Map<String, String> mappingRule = buildMappingRule(mappings);
        log.info("标签映射规则: {}", mappingRule);

        // 修改：如果没有映射规则，保留所有标注
        List<AnnotationRecord> filtered;
        if (mappingRule.isEmpty()) {
            log.info("没有标签映射规则，保留所有标注");
            filtered = annotations.stream()
                    .filter(record -> record != null && StrUtil.isNotBlank(record.getLabel()))
                    .collect(Collectors.toList());
        } else {
            // 有映射规则时，只保留在映射中的标签
            filtered = annotations.stream()
                    .filter(record -> record != null && StrUtil.isNotBlank(record.getLabel()))
                    .filter(record -> mappingRule.containsKey(record.getLabel()))
                    .collect(Collectors.toList());
        }

        // 修改：根据是否有映射规则决定是否应用标签映射
        List<AnnotationRecord> merged;
        if (mappingRule.isEmpty()) {
            // 没有映射规则，直接使用原始标签
            merged = filtered.stream()
                    .map(record -> record.toBuilder().build())  // 保持原样
                    .collect(Collectors.toList());
        } else {
            // 有映射规则，应用映射
            merged = filtered.stream()
                    .map(record -> record.toBuilder()
                            .label(mappingRule.get(record.getLabel()))
                            .build())
                    .collect(Collectors.toList());
        }

        Set<String> originalLabels = annotations.stream()
                .filter(record -> record != null && StrUtil.isNotBlank(record.getLabel()))
                .map(AnnotationRecord::getLabel)
                .collect(Collectors.toSet());

        Set<String> mappedLabels = mappingRule.keySet();
        List<String> removedLabels = originalLabels.stream()
                .filter(label -> !mappedLabels.contains(label))
                .collect(Collectors.toList());

        Set<String> mergedLabelSet = merged.stream()
                .map(AnnotationRecord::getLabel)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("标签合并完成 - 原始标签: {}, 合并后标签: {}, 移除标签: {}",
                originalLabels, mergedLabelSet, removedLabels);

        return LabelMergeResult.builder()
                .mergedAnnotations(merged)
                .mergedLabels(new ArrayList<>(mergedLabelSet))
                .removedLabels(removedLabels)
                .classCount(mergedLabelSet.size())
                .totalAnnotations(merged.size())
                .fileFiltered(filtered.size() != annotations.size())
                .build();
    }

    /**
     * 构建标签映射规则，并检测冲突。
     */
    public Map<String, String> buildMappingRule(List<TaskDatasetLabelMappingVO> mappings) {
        if (CollUtil.isEmpty(mappings)) {
            return Collections.emptyMap();
        }
        Map<String, String> rule = new LinkedHashMap<>();
        for (TaskDatasetLabelMappingVO mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            String source = StrUtil.trim(mapping.getSourceLabel());
            String target = StrUtil.trim(mapping.getTargetLabel());
            if (StrUtil.hasBlank(source, target)) {
                throw new IllegalArgumentException("标签映射存在空白值");
            }
            String existTarget = rule.get(source);
            if (existTarget != null && !StrUtil.equals(existTarget, target)) {
                throw new IllegalArgumentException(
                        String.format("源标签 %s 同时映射到 %s 和 %s，存在冲突", source, existTarget, target));
            }
            rule.put(source, target);
        }
        return rule;
    }

    /**
     * 标注记录
     */
    @Data
    @Builder(toBuilder = true)
    public static class AnnotationRecord {
        private Long id;
        private Long imageId;
        private String label;
        private Object geometry;
        private Map<String, Object> attributes;
        @Default
        private long count = 1L;
        private JSONObject raw;
    }

    /**
     * 合并结果
     */
    @Data
    @Builder
    public static class LabelMergeResult {
        private List<AnnotationRecord> mergedAnnotations;
        private List<String> mergedLabels;
        private List<String> removedLabels;
        private int classCount;
        private int totalAnnotations;
        private boolean fileFiltered;

        static LabelMergeResult empty() {
            return LabelMergeResult.builder()
                    .mergedAnnotations(Collections.emptyList())
                    .mergedLabels(Collections.emptyList())
                    .removedLabels(Collections.emptyList())
                    .classCount(0)
                    .totalAnnotations(0)
                    .fileFiltered(false)
                    .build();
        }
    }
}