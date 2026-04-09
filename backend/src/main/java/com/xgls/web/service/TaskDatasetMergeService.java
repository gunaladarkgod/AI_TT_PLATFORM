package com.xgls.web.service;

/*
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.mapper.TaskDatasetMapper;
import com.xgls.web.service.FileStorageService.FilterResult;
import com.xgls.web.vo.dataset.TaskDatasetMergePretrainRequestVO;
import com.xgls.web.vo.dataset.TaskDatasetMergeTargetRequestVO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TaskDatasetMergeService {

    @Autowired
    private TaskDatasetMapper taskDatasetMapper;
    @Autowired
    private OriginalDatasetService originalDatasetService;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private TaskDatasetService taskDatasetService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> mergeTargetSubset(TaskDatasetMergeTargetRequestVO request) throws IOException {
        if (request == null || CollectionUtils.isEmpty(request.getSelectedCoreSubsetIds())) {
            return Collections.singletonMap("error", "请选择需要合并的核心子集");
        }
        String datasetName = StrUtil.trimToNull(request.getTaskDatasetName());
        if (datasetName != null
                && taskDatasetService.lambdaQuery().eq(TaskDataset::getName, datasetName).one() != null) {
            return Collections.singletonMap("error", "数据集名称已存在");
        }

        List<Long> selectedIds = request.getSelectedCoreSubsetIds();
        List<OriginalDataset> coreSubsets = originalDatasetService.listByIds(selectedIds);
        if (coreSubsets.size() != selectedIds.size()) {
            return Collections.singletonMap("error", "部分核心子集不存在");
        }

        OriginalDataset reference = coreSubsets.get(0);
        String resolvedName = datasetName != null ? datasetName
                : coreSubsets.stream()
                .map(OriginalDataset::getName)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(reference.getName());

        TaskDataset taskDataset = new TaskDataset();
        taskDataset.setName(resolvedName);
        taskDataset.setSensorType(StrUtil.firstNonBlank(request.getSensorType(), reference.getSensorType()));
        taskDataset.setTargetType(StrUtil.firstNonBlank(request.getTargetType(), reference.getTargetType()));
        taskDataset.setDataFormat(reference.getDataFormat());
        taskDataset.setUsername(request.getUsername());
        taskDataset.setCreatedTime(LocalDateTime.now());

        setCoreSubsetMeta(taskDataset, selectedIds, coreSubsets);
        initializeSupSubsetMeta(taskDataset);

        // 修改这里：使用 MyBatis-Plus 的 insert 方法替代 insertTaskDataset
        if (taskDatasetMapper.insert(taskDataset) <= 0) {
            throw new IllegalStateException("创建任务数据集失败");
        }

        fileStorageService.generateTaskDatasetPath(taskDataset.getId());
        String targetImagesPath = fileStorageService.getTargetSubsetImagesPath(taskDataset.getId());
        String targetAnnoPath = fileStorageService.getTargetSubsetAnnotationsPath(taskDataset.getId());

        FilterResult coreResult = fileStorageService.copyAndFilterMultipleDatasets(
                coreSubsets, targetImagesPath, targetAnnoPath, request.getLabelMapping());
        applyCoreResult(taskDataset, coreResult, targetImagesPath, targetAnnoPath);
        taskDatasetMapper.updateById(taskDataset);

        return Map.of(
                "success", true,
                "task_dataset_id", taskDataset.getId(),
                "core_result", coreResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> mergePretrainSubset(TaskDatasetMergePretrainRequestVO request) throws IOException {
        if (request == null || CollectionUtils.isEmpty(request.getSelectedAuxiliarySubsetIds())) {
            return Collections.singletonMap("error", "请选择需要合并的辅助子集");
        }
        TaskDataset taskDataset = taskDatasetService.getById(request.getTaskDatasetId());
        if (taskDataset == null) {
            return Collections.singletonMap("error", "任务数据集不存在");
        }

        List<Long> supIds = request.getSelectedAuxiliarySubsetIds();
        List<OriginalDataset> supSubsets = originalDatasetService.listByIds(supIds);
        if (supSubsets.size() != supIds.size()) {
            return Collections.singletonMap("error", "部分辅助子集不存在");
        }

        setSupSubsetMeta(taskDataset, supIds, supSubsets);

        fileStorageService.generateTaskDatasetPath(taskDataset.getId());
        String supImagesPath = fileStorageService.getPretrainSubsetImagesPath(taskDataset.getId());
        String supAnnoPath = fileStorageService.getPretrainSubsetAnnotationsPath(taskDataset.getId());

        FilterResult supResult = fileStorageService.copyAndFilterMultipleDatasets(
                supSubsets, supImagesPath, supAnnoPath, request.getLabelMapping());
        applySupResult(taskDataset, supResult, supImagesPath, supAnnoPath);
        taskDatasetMapper.updateById(taskDataset);

        return Map.of(
                "success", true,
                "task_dataset_id", taskDataset.getId(),
                "sup_result", supResult);
    }

    private void setCoreSubsetMeta(TaskDataset taskDataset, List<Long> ids, List<OriginalDataset> subsets) {
        taskDataset.setCoreId(joinIds(ids));
        taskDataset.setCoreName(joinNames(subsets));
    }

    private void initializeSupSubsetMeta(TaskDataset taskDataset) {
        taskDataset.setSupId("");
        taskDataset.setSupName("");
        taskDataset.setSupImgNum(0);
        taskDataset.setSupAnnoNum(0);
        taskDataset.setSupClassNum(0);
        taskDataset.setSupClassList("[]");
        taskDataset.setSupDataPath("");
        taskDataset.setSupAnnoPath("");
    }

    private void setSupSubsetMeta(TaskDataset taskDataset, List<Long> ids, List<OriginalDataset> subsets) {
        taskDataset.setSupId(joinIds(ids));
        taskDataset.setSupName(joinNames(subsets));
    }

    private void applyCoreResult(TaskDataset taskDataset, FilterResult result, String dataPath, String annoPath) {
        taskDataset.setCoreImgNum(result.getKeptImages());
        taskDataset.setCoreAnnoNum(result.getKeptAnnotations());
        taskDataset.setCoreClassNum(result.getKeptLabels().size());
        taskDataset.setCoreClassList(JSONUtil.toJsonStr(result.getKeptLabels()));
        taskDataset.setCoreDataPath(dataPath);
        taskDataset.setCoreAnnoPath(annoPath);
    }

    private void applySupResult(TaskDataset taskDataset, FilterResult result, String dataPath, String annoPath) {
        taskDataset.setSupImgNum(result.getKeptImages());
        taskDataset.setSupAnnoNum(result.getKeptAnnotations());
        taskDataset.setSupClassNum(result.getKeptLabels().size());
        taskDataset.setSupClassList(JSONUtil.toJsonStr(result.getKeptLabels()));
        taskDataset.setSupDataPath(dataPath);
        taskDataset.setSupAnnoPath(annoPath);
    }

    private String joinIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return "";
        }
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("_"));
    }

    private String joinNames(List<OriginalDataset> datasets) {
        if (CollUtil.isEmpty(datasets)) {
            return "";
        }
        return datasets.stream()
                .map(OriginalDataset::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(","));
    }
}

 */