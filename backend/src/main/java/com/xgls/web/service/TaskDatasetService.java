package com.xgls.web.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import com.xgls.web.base.AjaxResult;
import java.net.URLDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.mapper.TaskDatasetMapper;
import com.xgls.web.service.FileStorageService.FilterResult;
import com.xgls.web.vo.dataset.TaskDatasetMergePretrainRequestVO;
import com.xgls.web.vo.dataset.TaskDatasetMergeTargetRequestVO;
import com.xgls.web.vo.dataset.TaskDatasetLabelMappingVO;
import com.xgls.web.vo.dataset.TaskDatasetSubsetsInfoVO;
import com.xgls.web.vo.dataset.TaskDatasetSubsetItemVO;
import com.xgls.web.vo.dataset.TaskDatasetUploadTemplateVO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TaskDatasetService extends ServiceImpl<TaskDatasetMapper, TaskDataset> {

    @Value("${sys.root-upload}")
    private String rootPath;

    @Autowired
    private OriginalDatasetService originalDatasetService;

    @Autowired
    private FileStorageService fileStorageService;

    // ========== 子集信息相关方法 ==========

    /**
     * 获取子集信息
     */
    public TaskDatasetSubsetsInfoVO getSubsetsInfo(Long taskDatasetId, Boolean isPretrain) {
        log.info("获取子集信息 - taskDatasetId: {}, isPretrain: {}", taskDatasetId, isPretrain);

        TaskDataset baseDataset = taskDatasetId != null ? getById(taskDatasetId) : getLatestTaskDataset();
        if (baseDataset == null) {
            log.warn("未找到任务数据集记录");
            return null;
        }

        log.info("找到任务数据集: ID={}, Name={}", baseDataset.getId(), baseDataset.getName());

        return buildSubsetsInfoVO(baseDataset);
    }

    /**
     * 构建子集信息VO
     */
    private TaskDatasetSubsetsInfoVO buildSubsetsInfoVO(TaskDataset taskDataset) {
        log.info("=== 构建子集信息VO ===");

        List<Long> coreIds = parseIdList(taskDataset.getCoreId());
        List<Long> supIds = parseIdList(taskDataset.getSupId());

        log.info("解析的coreIds: {}", coreIds);
        log.info("解析的supIds: {}", supIds);

        List<TaskDatasetSubsetItemVO> coreSubsets = CollUtil.isEmpty(coreIds)
                ? Collections.emptyList()
                : toSubsetItems(originalDatasetService.listByIds(coreIds));
        List<TaskDatasetSubsetItemVO> supSubsets = CollUtil.isEmpty(supIds)
                ? Collections.emptyList()
                : toSubsetItems(originalDatasetService.listByIds(supIds));

        log.info("找到的核心子集数量: {}", coreSubsets.size());
        log.info("找到的辅助子集数量: {}", supSubsets.size());

        return TaskDatasetSubsetsInfoVO.builder()
                .taskDataset(taskDataset)
                .coreSubsets(coreSubsets)
                .auxiliarySubsets(supSubsets)
                .build();
    }

    // ========== 目标子集合成方法 ==========

    /**
     * 更新目标子集 - 完整版本
     */
    public Map<String, Object> updateTargetSubsets(TaskDatasetMergeTargetRequestVO request) throws IOException {
        log.info("=== 开始目标子集合成 ===");

        // 1. 参数验证
        if (request == null || CollectionUtils.isEmpty(request.getSelectedCoreSubsetIds())) {
            return createErrorResult("请选择需要合并的核心子集");
        }

        if (StrUtil.isBlank(request.getTaskDatasetName())) {
            return createErrorResult("模板名称不能为空");
        }

        log.info("✅ 目标子集参数验证通过");
        log.info("  模板名称: '{}'", request.getTaskDatasetName());
        log.info("  传感器类型: '{}'", request.getSensorType());
        log.info("  目标类型: '{}'", request.getTargetType());
        log.info("  核心子集: {}", request.getSelectedCoreSubsetIds());
        log.info("  用户名: {}", request.getUsername());

        // 2. 获取任务数据集
        TaskDataset taskDataset = getLatestTaskDataset();
        if (taskDataset == null) {
            return createErrorResult("未找到可用的任务数据集记录");
        }

        log.info("✅ 找到任务数据集: ID={}", taskDataset.getId());

        // 3. 直接设置任务数据集信息
        setDatasetInfoFromRequest(taskDataset, request);

        // 4. 处理核心子集
        Map<String, Object> processResult = processCoreSubsets(taskDataset, request);
        if (!(Boolean) processResult.get("success")) {
            return processResult;
        }

        FilterResult coreResult = (FilterResult) processResult.get("core_result");

        // 5. 更新数据库
        boolean updateSuccess = updateById(taskDataset);
        log.info("数据库更新结果: {}", updateSuccess);

        if (updateSuccess) {
            // 验证更新
            TaskDataset updated = getById(taskDataset.getId());
            log.info("✅ 更新验证 - sensorType: '{}', targetType: '{}'",
                    updated.getSensorType(), updated.getTargetType());
        }

        // 6. 返回结果
        return createSuccessResult(taskDataset, coreResult, "target");
    }

    /**
     * 直接从请求设置数据集信息
     */
    private void setDatasetInfoFromRequest(TaskDataset taskDataset, TaskDatasetMergeTargetRequestVO request) {
        log.info("=== 直接从请求设置数据集信息 ===");

        // 设置基本名称
        taskDataset.setName(StrUtil.trimToEmpty(request.getTaskDatasetName()));

        // 直接使用前端传入的中文值
        taskDataset.setSensorType(StrUtil.trimToEmpty(request.getSensorType()));
        taskDataset.setTargetType(StrUtil.trimToEmpty(request.getTargetType()));

        // 设置用户名
        taskDataset.setUsername(request.getUsername());

        log.info("✅ 直接设置 - name: '{}', sensorType: '{}', targetType: '{}', username: '{}'",
                taskDataset.getName(), taskDataset.getSensorType(),
                taskDataset.getTargetType(), taskDataset.getUsername());
    }

    /**
     * 处理核心子集 - 添加详细的标签分析
     */
    private Map<String, Object> processCoreSubsets(TaskDataset taskDataset,
                                                   TaskDatasetMergeTargetRequestVO request) throws IOException {
        log.info("�� 处理核心子集数据");

        List<Long> coreSubsetIds = request.getSelectedCoreSubsetIds();
        List<OriginalDataset> coreSubsets = originalDatasetService.listByIds(coreSubsetIds);

        if (coreSubsets.size() != coreSubsetIds.size()) {
            return createErrorResult("核心子集查询不完整，期望: " + coreSubsetIds.size() + ", 实际: " + coreSubsets.size());
        }

        log.info("✅ 找到 {} 个核心子集", coreSubsets.size());

        // 详细的标签分析
        log.info("=== 核心子集详细分析 ===");
        for (OriginalDataset dataset : coreSubsets) {
            log.info("数据集: {} (ID: {})", dataset.getName(), dataset.getId());
            log.info("  数据路径: {}", dataset.getDataPath());
            log.info("  标注路径: {}", dataset.getAnnoPath());
            log.info("  标签列表: {}", dataset.getClassList());
            log.info("  图片数量: {}", dataset.getImgNum());
            log.info("  标注数量: {}", dataset.getAnnoNum());
        }

        log.info("=== 标签映射分析 ===");
        log.info("前端发送的标签映射规则:");
        if (CollUtil.isEmpty(request.getLabelMapping())) {
            log.info("  无标签映射规则");
        } else {
            for (TaskDatasetLabelMappingVO mapping : request.getLabelMapping()) {
                log.info("  {} -> {}", mapping.getSourceLabel(), mapping.getTargetLabel());
            }
        }

        // 更新核心子集ID和名称
        String coreIdStr = coreSubsetIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("_"));
        taskDataset.setCoreId(coreIdStr);

        // 修改：coreName 使用下划线分隔而不是逗号
        String coreName = coreSubsets.stream()
                .map(OriginalDataset::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("_"));
        taskDataset.setCoreName(coreName);
        log.info("设置 coreId: {}, coreName: {}", coreIdStr, coreName);

        // 文件操作
        fileStorageService.generateTaskDatasetPath(taskDataset.getId());
        String targetImagesPath = fileStorageService.getTargetSubsetImagesPath(taskDataset.getId());
        String targetAnnoPath = fileStorageService.getTargetSubsetAnnotationsPath(taskDataset.getId());

        log.info("�� 文件路径 - 图片: {}, 标注: {}", targetImagesPath, targetAnnoPath);

        // 复制和过滤数据
        FilterResult coreResult = fileStorageService.copyAndFilterMultipleDatasets(
                coreSubsets, targetImagesPath, targetAnnoPath, request.getLabelMapping());

        log.info("�� 文件处理结果 - 图片: {}, 标注: {}, 标签: {}, 标签统计: {}",
                coreResult.getKeptImages(), coreResult.getKeptAnnotations(),
                coreResult.getKeptLabels(), coreResult.getLabelStatistics());

        // 应用结果
        applyCoreResult(taskDataset, coreResult, targetImagesPath, targetAnnoPath);

        return Map.of("success", true, "core_result", coreResult);
    }

    // ========== 预训练子集合成方法 ==========

    /**
     * 更新预训练子集
     */
    public Map<String, Object> updatePretrainSubsets(TaskDatasetMergePretrainRequestVO request) throws IOException {
        log.info("=== 开始预训练子集合成 ===");

        // 参数验证
        if (request == null || CollectionUtils.isEmpty(request.getSelectedAuxiliarySubsetIds())) {
            return createErrorResult("请选择需要合并的辅助子集");
        }

        // 获取任务数据集
        TaskDataset taskDataset = request.getTaskDatasetId() != null ?
                getById(request.getTaskDatasetId()) : getLatestTaskDataset();
        if (taskDataset == null) {
            return createErrorResult("未找到对应的任务数据集");
        }

        log.info("✅ 找到任务数据集: ID={}", taskDataset.getId());
        taskDataset.setUsername(request.getUsername());

        // 处理辅助子集
        List<Long> supSubsetIds = request.getSelectedAuxiliarySubsetIds();
        List<OriginalDataset> supSubsets = originalDatasetService.listByIds(supSubsetIds);

        if (supSubsets.size() != supSubsetIds.size()) {
            return createErrorResult("辅助子集查询不完整，期望: " + supSubsetIds.size() + ", 实际: " + supSubsets.size());
        }

        log.info("✅ 找到 {} 个辅助子集", supSubsets.size());

        // 更新辅助子集ID和名称
        String supIdStr = supSubsetIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("_"));
        taskDataset.setSupId(supIdStr);

        // 修改：supName 使用下划线分隔而不是逗号
        String supName = supSubsets.stream()
                .map(OriginalDataset::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("_"));
        taskDataset.setSupName(supName);
        log.info("设置 supId: {}, supName: {}", supIdStr, supName);

        // 文件操作
        fileStorageService.generateTaskDatasetPath(taskDataset.getId());
        String supImagesPath = fileStorageService.getPretrainSubsetImagesPath(taskDataset.getId());
        String supAnnoPath = fileStorageService.getPretrainSubsetAnnotationsPath(taskDataset.getId());

        log.info("�� 预训练文件路径 - 图片: {}, 标注: {}", supImagesPath, supAnnoPath);

        FilterResult supResult = fileStorageService.copyAndFilterMultipleDatasets(
                supSubsets, supImagesPath, supAnnoPath, request.getLabelMapping());

        log.info("�� 预训练处理结果 - 图片: {}, 标注: {}, 标签: {}",
                supResult.getKeptImages(), supResult.getKeptAnnotations(),
                supResult.getKeptLabels());

        // 应用结果
        applySupResult(taskDataset, supResult, supImagesPath, supAnnoPath);

        // 更新数据库
        boolean updateSuccess = updateById(taskDataset);
        log.info("数据库更新结果: {}", updateSuccess);

        return createSuccessResult(taskDataset, supResult, "pretrain");
    }

    // ========== 结果应用方法 ==========

    /**
     * 应用核心子集结果
     */
    private void applyCoreResult(TaskDataset taskDataset, FilterResult result, String dataPath, String annoPath) {
        log.info("�� 应用核心子集结果");

        taskDataset.setCoreImgNum(result.getKeptImages());
        taskDataset.setCoreAnnoNum(result.getKeptAnnotations());
        taskDataset.setCoreClassNum(result.getKeptLabels().size());

        // 使用标签统计JSON格式
        String labelStatisticsJson = result.getLabelStatisticsJson();
        taskDataset.setCoreClassList(labelStatisticsJson);

        taskDataset.setCoreDataPath(dataPath);
        taskDataset.setCoreAnnoPath(annoPath);

        log.info("✅ 核心子集更新完成 - 图片: {}, 标注: {}, 标签数: {}",
                result.getKeptImages(), result.getKeptAnnotations(), result.getKeptLabels().size());
    }

    /**
     * 应用辅助子集结果
     */
    private void applySupResult(TaskDataset taskDataset, FilterResult result, String dataPath, String annoPath) {
        log.info("�� 应用辅助子集结果");

        taskDataset.setSupImgNum(result.getKeptImages());
        taskDataset.setSupAnnoNum(result.getKeptAnnotations());
        taskDataset.setSupClassNum(result.getKeptLabels().size());

        // 使用标签统计JSON格式
        String labelStatisticsJson = result.getLabelStatisticsJson();
        taskDataset.setSupClassList(labelStatisticsJson);

        taskDataset.setSupDataPath(dataPath);
        taskDataset.setSupAnnoPath(annoPath);

        log.info("✅ 辅助子集更新完成 - 图片: {}, 标注: {}, 标签数: {}",
                result.getKeptImages(), result.getKeptAnnotations(), result.getKeptLabels().size());
    }

    // ========== 工具方法 ==========

    /**
     * 创建成功结果
     */
    private Map<String, Object> createSuccessResult(TaskDataset taskDataset, FilterResult result, String type) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("task_dataset_id", taskDataset.getId());
        response.put(type + "_subset_id", taskDataset.getId());
        response.put(type + "_result", result);
        response.put("template_info", Map.of(
                "name", taskDataset.getName(),
                "sensorType", taskDataset.getSensorType(),
                "targetType", taskDataset.getTargetType(),
                "coreName", taskDataset.getCoreName(),
                "supName", taskDataset.getSupName()
        ));
        response.put("label_statistics", result.getLabelStatistics());

        log.info("�� {}子集合成完成", "target".equals(type) ? "目标" : "预训练");
        return response;
    }

    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String errorMessage) {
        log.error("❌ 处理失败: {}", errorMessage);
        return Map.of("success", false, "error", errorMessage);
    }

    /**
     * 获取最新的任务数据集
     */
    public TaskDataset getLatestTaskDataset() {
        return lambdaQuery()
                .orderByDesc(TaskDataset::getCreatedTime)
                .last("LIMIT 1")
                .one();
    }

    // ========== 文件上传方法 ==========

    public TaskDatasetUploadTemplateVO uploadTemplate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = StrUtil.isNotBlank(originalFilename) && originalFilename.contains(".") ?
                originalFilename.substring(originalFilename.lastIndexOf(".")) : "";

        String newName = IdUtil.simpleUUID() + suffix;
        Path dir = Path.of(rootPath, CodeMap.DIR_TEMPLATE);
        Files.createDirectories(dir);
        Path dest = dir.resolve(newName);
        file.transferTo(dest);

        return TaskDatasetUploadTemplateVO.builder()
                .fileName(newName)
                .relativePath(Path.of(CodeMap.DIR_TEMPLATE, newName).toString().replace('\\', '/'))
                .absolutePath(dest.toString())
                .build();
    }

    // ========== 辅助方法 ==========

    private List<Long> parseIdList(String idString) {
        if (StrUtil.isBlank(idString)) {
            return Collections.emptyList();
        }
        return StrUtil.split(idString, '_').stream()
                .map(StrUtil::trim)
                .filter(StrUtil::isNotBlank)
                .map(id -> {
                    try {
                        return Long.valueOf(id);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<TaskDatasetSubsetItemVO> toSubsetItems(List<OriginalDataset> datasets) {
        if (CollUtil.isEmpty(datasets)) {
            return Collections.emptyList();
        }
        return datasets.stream()
                .map(this::toSubsetItem)
                .collect(Collectors.toList());
    }

    private TaskDatasetSubsetItemVO toSubsetItem(OriginalDataset dataset) {
        return TaskDatasetSubsetItemVO.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .sensorType(dataset.getSensorType())
                .classList(parseClassList(dataset.getClassList()))
                .dataPath(dataset.getDataPath())
                .annoPath(dataset.getAnnoPath())
                .imgNum(dataset.getImgNum())
                .annoNum(dataset.getAnnoNum())
                .build();
    }

    private List<String> parseClassList(String classListStr) {
        if (StrUtil.isBlank(classListStr)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        try {
            if (JSONUtil.isTypeJSONArray(classListStr)) {
                JSONArray array = JSONUtil.parseArray(classListStr);
                array.forEach(item -> {
                    if (item instanceof Map) {
                        Object targetLabel = ((Map<?, ?>) item).get("target_label");
                        if (targetLabel instanceof String) {
                            result.add((String) targetLabel);
                        }
                    } else if (item instanceof String) {
                        result.add((String) item);
                    }
                });
            } else if (JSONUtil.isTypeJSONObject(classListStr)) {
                JSONObject obj = JSONUtil.parseObj(classListStr);
                result.addAll(obj.keySet());
            } else {
                String[] parts = classListStr.split(",");
                for (String part : parts) {
                    if (StrUtil.isNotBlank(part)) {
                        result.add(part.trim());
                    }
                }
            }
        } catch (Exception e) {
            String[] parts = classListStr.split(",");
            for (String part : parts) {
                if (StrUtil.isNotBlank(part)) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    // ========== 任务数据集子集预览 & 图片直出 ==========

    /**
     * 预览任务数据集某个子集（core / sup）的图片
     */
    public AjaxResult previewSubset(Long taskDatasetId, String subset, Integer perLabel, String baseUrl) {
        if (taskDatasetId == null) {
            return AjaxResult.error("任务数据集ID不能为空");
        }
        if (StrUtil.isBlank(subset)) {
            return AjaxResult.error("subset 不能为空");
        }

        TaskDataset td = getById(taskDatasetId);
        if (td == null) {
            return AjaxResult.error("任务数据集不存在");
        }

        boolean isCore;
        if ("core".equalsIgnoreCase(subset)) {
            isCore = true;
        } else if ("sup".equalsIgnoreCase(subset) || "pretrain".equalsIgnoreCase(subset)) {
            isCore = false;
        } else {
            return AjaxResult.error("subset 仅支持 core 或 sup");
        }

        String classJson = isCore ? td.getCoreClassList() : td.getSupClassList();
        String dataPath  = isCore ? td.getCoreDataPath()  : td.getSupDataPath();
        String annoPath  = isCore ? td.getCoreAnnoPath()  : td.getSupAnnoPath();

        if (StrUtil.isBlank(dataPath) || StrUtil.isBlank(annoPath)) {
            return AjaxResult.error("该子集尚未生成数据路径");
        }

        int n = (perLabel == null || perLabel <= 0) ? 3 : Math.min(perLabel, 12);

        List<String> labelList = parseClassList(classJson);
        Set<String> labelSet = new LinkedHashSet<>(labelList);

        Path annDir = Path.of(annoPath).normalize();

        Map<String, List<String>> imgKeysByLabel = pickImagesFromDota(annDir, labelSet, n);

        // 若 classList 为空，则根据扫描结果补充标签集合
        if (labelSet.isEmpty()) {
            labelSet.addAll(imgKeysByLabel.keySet());
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (String label : labelSet) {
            List<String> imgKeys = imgKeysByLabel.getOrDefault(label, Collections.emptyList());
            List<String> urls = imgKeys.stream()
                    .map(k -> buildSubsetImageUrl(baseUrl, taskDatasetId, isCore ? "core" : "sup", k))
                    .collect(Collectors.toList());
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("label", label);
            one.put("images", urls);
            one.put("count", urls.size());
            items.add(one);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskDatasetId", taskDatasetId);
        payload.put("subset", isCore ? "core" : "sup");
        payload.put("perLabel", n);
        payload.put("items", items);

        return AjaxResult.success(payload);
    }

    /**
     * DOTA 标注目录扫描：按标签挑选若干图像 baseName
     */
    private Map<String, List<String>> pickImagesFromDota(Path annDir,
                                                         Set<String> labels,
                                                         int perLabel) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (labels != null) {
            for (String l : labels) {
                map.put(l, new ArrayList<>());
            }
        }

        if (annDir == null || !Files.exists(annDir) || !Files.isDirectory(annDir)) {
            return map;
        }

        try (java.util.stream.Stream<Path> stream = Files.list(annDir)) {
            List<Path> files = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> {
                        String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return fn.endsWith(".txt");
                    })
                    .sorted()
                    .collect(Collectors.toList());

            for (Path txt : files) {
                String baseName = stripExt(txt.getFileName().toString());
                List<String> lines = Files.readAllLines(txt, StandardCharsets.UTF_8);
                Set<String> labelsInThisImage = new LinkedHashSet<>();

                for (String line : lines) {
                    if (StrUtil.isBlank(line)) continue;
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 9) continue; // 至少 8 坐标 + 类别
                    String label = parts[8];
                    if (labels != null && !labels.isEmpty() && !labels.contains(label)) {
                        continue;
                    }
                    labelsInThisImage.add(label);
                }

                for (String label : labelsInThisImage) {
                    List<String> list = map.computeIfAbsent(label, k -> new ArrayList<>());
                    if (list.size() < perLabel) {
                        list.add(baseName);
                    }
                }

                if (labels != null && !labels.isEmpty() && allFull(map, labels, perLabel)) {
                    break;
                }
            }
        } catch (IOException e) {
            log.warn("扫描 DOTA 标注目录失败: {}", annDir, e);
        }
        return map;
    }

    private boolean allFull(Map<String, List<String>> map,
                            Set<String> labels,
                            int perLabel) {
        for (String label : labels) {
            List<String> list = map.get(label);
            if (list == null || list.size() < perLabel) {
                return false;
            }
        }
        return true;
    }

    private String stripExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    private String buildSubsetImageUrl(String baseUrl,
                                       Long taskDatasetId,
                                       String subset,
                                       String imgKey) {
        String encoded = urlEncode(imgKey);
        return baseUrl + "/taskDataset/" + taskDatasetId + "/subset-image?subset=" + subset + "&img=" + encoded;
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 任务数据集子集图片二进制直出
     */
    public void streamSubsetImage(Long taskDatasetId,
                                  String subset,
                                  String imgKey,
                                  jakarta.servlet.http.HttpServletResponse resp) throws IOException {
        if (taskDatasetId == null || StrUtil.isBlank(imgKey)) {
            resp.setStatus(404);
            return;
        }

        TaskDataset td = getById(taskDatasetId);
        if (td == null) {
            resp.setStatus(404);
            return;
        }

        boolean isCore;
        if ("core".equalsIgnoreCase(subset)) {
            isCore = true;
        } else if ("sup".equalsIgnoreCase(subset) || "pretrain".equalsIgnoreCase(subset)) {
            isCore = false;
        } else {
            resp.setStatus(404);
            return;
        }

        String dataPath = isCore ? td.getCoreDataPath() : td.getSupDataPath();
        if (StrUtil.isBlank(dataPath)) {
            resp.setStatus(404);
            return;
        }

        Path imagesDir = Path.of(dataPath).normalize();
        String decoded = java.net.URLDecoder.decode(imgKey, StandardCharsets.UTF_8);
        String baseName = stripExt(decoded);

        Path file = findImageByBaseName(imagesDir, baseName);
        if (file == null || !file.startsWith(imagesDir) || !Files.exists(file) || Files.isDirectory(file)) {
            resp.setStatus(404);
            return;
        }

        String ctype = Files.probeContentType(file);
        if (StrUtil.isBlank(ctype)) {
            String fnLow = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fnLow.endsWith(".jpg") || fnLow.endsWith(".jpeg")) ctype = "image/jpeg";
            else if (fnLow.endsWith(".png")) ctype = "image/png";
            else if (fnLow.endsWith(".bmp")) ctype = "image/bmp";
            else if (fnLow.endsWith(".webp")) ctype = "image/webp";
            else ctype = "application/octet-stream";
        }
        resp.setContentType(ctype);
        resp.setHeader("Cache-Control", "max-age=3600");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        try (var os = resp.getOutputStream()) {
            Files.copy(file, os);
            os.flush();
        }
    }

    private Path findImageByBaseName(Path imagesDir, String baseName) {
        if (!Files.exists(imagesDir) || !Files.isDirectory(imagesDir)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(imagesDir)) {
            return stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> {
                        String fn = p.getFileName().toString();
                        String bn = stripExt(fn);
                        return bn.equals(baseName);
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("查找图片失败, dir={}, base={}", imagesDir, baseName, e);
            return null;
        }
    }

    /**
     * 读取任务数据集子集（core/sup）的 DOTA 标注，并返回多边形框
     */
    public AjaxResult getSubsetDotaObjects(Long taskDatasetId, String subset, String imgName) {
        if (taskDatasetId == null || StrUtil.isBlank(subset) || StrUtil.isBlank(imgName)) {
            return AjaxResult.error("参数缺失");
        }

        subset = subset.toLowerCase(Locale.ROOT);

        TaskDataset td = this.getById(taskDatasetId);
        if (td == null) {
            return AjaxResult.error("任务数据集不存在");
        }

        // 根据 subset 选择 core/sup 路径
        String imagesPath;
        String annoPath;
        if ("core".equals(subset) || "target".equals(subset)) {
            imagesPath = td.getCoreDataPath();
            annoPath   = td.getCoreAnnoPath();
        } else if ("sup".equals(subset) || "pretrain".equals(subset)) {
            imagesPath = td.getSupDataPath();
            annoPath   = td.getSupAnnoPath();
        } else {
            return AjaxResult.error("非法子集类型");
        }

        if (StrUtil.isBlank(imagesPath) || StrUtil.isBlank(annoPath)) {
            return AjaxResult.error("任务数据集缺少子集路径配置");
        }

        try {
            java.nio.file.Path imagesDir = java.nio.file.Path.of(imagesPath).normalize();
            java.nio.file.Path annDir    = java.nio.file.Path.of(annoPath).normalize();

            String decodedImg = java.net.URLDecoder.decode(imgName, java.nio.charset.StandardCharsets.UTF_8);
            String baseName   = decodedImg.replaceAll("\\.[^.]+$", "");

            // 查找图片文件
            java.nio.file.Path imgFile = null;
            java.nio.file.Path direct = imagesDir.resolve(decodedImg).normalize();
            if (direct.startsWith(imagesDir)
                    && java.nio.file.Files.exists(direct)
                    && !java.nio.file.Files.isDirectory(direct)) {
                imgFile = direct;
            } else {
                String[] exts = {".jpg", ".jpeg", ".png", ".bmp", ".webp"};
                for (String ext : exts) {
                    java.nio.file.Path cand = imagesDir.resolve(baseName + ext).normalize();
                    if (cand.startsWith(imagesDir)
                            && java.nio.file.Files.exists(cand)
                            && !java.nio.file.Files.isDirectory(cand)) {
                        imgFile = cand;
                        break;
                    }
                }
            }

            if (imgFile == null) {
                return AjaxResult.error("图片不存在");
            }

            // 标注文件
            java.nio.file.Path txtFile = annDir.resolve(baseName + ".txt").normalize();

            if (!txtFile.startsWith(annDir)
                    || !java.nio.file.Files.exists(txtFile)
                    || java.nio.file.Files.isDirectory(txtFile)) {
                // 没有标注：返回宽高 + 空 objects
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                int[] wh = readImageWH(imgFile);
                payload.put("width",  wh[0]);
                payload.put("height", wh[1]);
                payload.put("objects", java.util.List.of());
                return AjaxResult.success(payload);
            }

            // 解析 DOTA txt
            java.util.List<String> lines = java.nio.file.Files.readAllLines(
                    txtFile, java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<Map<String, Object>> objects = new java.util.ArrayList<>();

            for (String line : lines) {
                if (StrUtil.isBlank(line)) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 9) continue;

                double[] v = new double[8];
                try {
                    for (int i = 0; i < 8; i++) {
                        v[i] = Double.parseDouble(parts[i]);
                    }
                } catch (Exception e) {
                    continue;
                }

                String label = parts[8];
                int difficult = 0;
                if (parts.length >= 10) {
                    try {
                        difficult = Integer.parseInt(parts[9]);
                    } catch (Exception ignore) {
                    }
                }

                java.util.List<java.util.List<Double>> pts = java.util.List.of(
                        java.util.List.of(v[0], v[1]),
                        java.util.List.of(v[2], v[3]),
                        java.util.List.of(v[4], v[5]),
                        java.util.List.of(v[6], v[7])
                );

                Map<String, Object> one = new java.util.LinkedHashMap<>();
                one.put("points", pts);
                one.put("label", label);
                one.put("difficult", difficult);
                objects.add(one);
            }

            int[] wh = readImageWH(imgFile);

            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("width",  wh[0]);
            payload.put("height", wh[1]);
            payload.put("objects", objects);
            return AjaxResult.success(payload);

        } catch (Exception e) {
            log.warn("getSubsetDotaObjects failed, taskDatasetId={}, subset={}, imgName={}",
                    taskDatasetId, subset, imgName, e);
            return AjaxResult.error("读取标注失败");
        }
    }

    /**
     * 读取图片宽高
     */
    private int[] readImageWH(java.nio.file.Path imgFile) {
        int w = 0, h = 0;
        try {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(imgFile.toFile());
            if (bi != null) {
                w = bi.getWidth();
                h = bi.getHeight();
            }
        } catch (Exception ignore) {
        }
        return new int[]{w, h};
    }
}