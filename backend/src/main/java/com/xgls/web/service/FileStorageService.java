package com.xgls.web.service;

import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.vo.dataset.TaskDatasetLabelMappingVO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileStorageService {

    private static final String TARGET_SUBSET_DIR = "target_subset";
    private static final String PRETRAIN_SUBSET_DIR = "pretrain_subset";
    private static final String IMAGES_DIR = "images";
    private static final String ANNOTATIONS_DIR = "annotations";
    private static final String[] IMAGE_EXTENSIONS = new String[] { ".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff" };

    @Value("${sys.task-dataset-root:/home/cs303-1/AI_TT_Platform/data/task_dataset}")
    private String taskDatasetRoot;

    public String generateTaskDatasetPath(Long taskDatasetId) {
        if (taskDatasetId == null) {
            throw new IllegalArgumentException("taskDatasetId 不能为空");
        }
        Path basePath = Paths.get(getTaskDatasetRoot(), taskDatasetId.toString());
        createDirectories(basePath);
        createSubsetStructure(basePath.resolve(TARGET_SUBSET_DIR));
        createSubsetStructure(basePath.resolve(PRETRAIN_SUBSET_DIR));
        return basePath.toString();
    }

    public String getTargetSubsetImagesPath(Long taskDatasetId) {
        return Paths.get(getTaskDatasetRoot(), taskDatasetId.toString(), TARGET_SUBSET_DIR, IMAGES_DIR).toString();
    }

    public String getTargetSubsetAnnotationsPath(Long taskDatasetId) {
        return Paths.get(getTaskDatasetRoot(), taskDatasetId.toString(), TARGET_SUBSET_DIR, ANNOTATIONS_DIR).toString();
    }

    public String getPretrainSubsetImagesPath(Long taskDatasetId) {
        return Paths.get(getTaskDatasetRoot(), taskDatasetId.toString(), PRETRAIN_SUBSET_DIR, IMAGES_DIR).toString();
    }

    public String getPretrainSubsetAnnotationsPath(Long taskDatasetId) {
        return Paths.get(getTaskDatasetRoot(), taskDatasetId.toString(), PRETRAIN_SUBSET_DIR, ANNOTATIONS_DIR).toString();
    }

    public FilterResult copyAndFilterMultipleDatasets(List<OriginalDataset> datasets,
                                                      String targetImagesPath,
                                                      String targetAnnoPath,
                                                      List<TaskDatasetLabelMappingVO> mappings) throws IOException {
        log.info("开始处理多个数据集，数量: {}", datasets.size());
        if (CollUtil.isEmpty(datasets)) {
            return FilterResult.empty();
        }

        Path targetImagesDir = Paths.get(targetImagesPath);
        Path targetAnnoDir = Paths.get(targetAnnoPath);
        createDirectories(targetImagesDir);
        createDirectories(targetAnnoDir);

        int keptImages = 0;
        int keptAnnotations = 0;
        Set<String> keptLabels = new LinkedHashSet<>();
        Set<String> removedLabels = new LinkedHashSet<>();
        boolean filteredAny = false;
        Map<String, Integer> totalLabelStatistics = new HashMap<>();

        // 构建标签映射规则
        Map<String, String> mappingRule = buildMappingRule(mappings);
        log.info("标签映射规则: {}", mappingRule);

        for (OriginalDataset dataset : datasets) {
            log.info("处理数据集: ID={}, Name={}, ProjectId={}",
                    dataset.getId(), dataset.getName(), dataset.getProjectId());

            FilterResult result = processDatasetWithProjectIdNaming(dataset, targetImagesDir,
                    targetAnnoDir, mappingRule);

            keptImages += result.getKeptImages();
            keptAnnotations += result.getKeptAnnotations();
            keptLabels.addAll(result.getKeptLabels());
            removedLabels.addAll(result.getRemovedLabels());
            filteredAny |= result.isFiltered();

            // 合并标签统计
            mergeLabelStatistics(totalLabelStatistics, result.getLabelStatistics());
        }

        log.info("所有数据集处理完成 - 总保留图片: {}, 总保留标注: {}, 总保留标签: {}, 总标签统计: {}",
                keptImages, keptAnnotations, keptLabels, totalLabelStatistics);

        return FilterResult.builder()
                .keptImages(keptImages)
                .keptAnnotations(keptAnnotations)
                .keptLabels(keptLabels)
                .removedLabels(removedLabels)
                .filtered(filteredAny)
                .labelStatistics(totalLabelStatistics)
                .build();
    }

    /**
     * 处理单个数据集并使用 projectid_原文件名 命名
     */
    private FilterResult processDatasetWithProjectIdNaming(OriginalDataset dataset,
                                                           Path targetImagesDir,
                                                           Path targetAnnoDir,
                                                           Map<String, String> mappingRule) throws IOException {
        log.info("处理数据集: ID={}, Name={}, ProjectId={}",
                dataset.getId(), dataset.getName(), dataset.getProjectId());

        String sourceImagesPath = dataset.getDataPath();
        String sourceAnnoPath = dataset.getAnnoPath();

        if (StrUtil.isBlank(sourceAnnoPath)) {
            log.warn("源标注路径为空，跳过处理");
            return FilterResult.empty();
        }

        Path sourceAnnoDir = Paths.get(sourceAnnoPath);
        if (!Files.exists(sourceAnnoDir)) {
            log.warn("标注目录不存在: {}", sourceAnnoDir);
            return FilterResult.empty();
        }

        Path sourceImagesDir = StrUtil.isBlank(sourceImagesPath) ? null : Paths.get(sourceImagesPath);

        // 查找所有标注文件
        List<Path> annotationFiles;
        try (var stream = Files.walk(sourceAnnoDir)) {
            annotationFiles = stream
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".txt");
                    })
                    .collect(Collectors.toList());
        }

        log.info("找到标注文件数量: {}", annotationFiles.size());

        int keptImages = 0;
        int keptAnnotations = 0;
        Set<String> keptLabels = new LinkedHashSet<>();
        Set<String> removedLabels = new LinkedHashSet<>();
        Map<String, Integer> labelStatistics = new HashMap<>();

        for (Path annotationFile : annotationFiles) {
            log.info("处理标注文件: {}", annotationFile);

            FilterResult result = processAnnotationFileWithProjectIdNaming(annotationFile, sourceImagesDir,
                    targetImagesDir, targetAnnoDir, sourceAnnoDir, mappingRule, dataset.getProjectId());

            keptImages += result.getKeptImages();
            keptAnnotations += result.getKeptAnnotations();
            keptLabels.addAll(result.getKeptLabels());
            removedLabels.addAll(result.getRemovedLabels());
            mergeLabelStatistics(labelStatistics, result.getLabelStatistics());
        }

        boolean filteredAny = !removedLabels.isEmpty();

        log.info("数据集处理完成 - 保留图片: {}, 保留标注: {}, 保留标签: {}, 标签统计: {}",
                keptImages, keptAnnotations, keptLabels, labelStatistics);

        return FilterResult.builder()
                .keptImages(keptImages)
                .keptAnnotations(keptAnnotations)
                .keptLabels(keptLabels)
                .removedLabels(removedLabels)
                .filtered(filteredAny)
                .labelStatistics(labelStatistics)
                .build();
    }

    /**
     * 处理单个标注文件并使用 projectid_原文件名 命名
     */
    private FilterResult processAnnotationFileWithProjectIdNaming(Path annotationFile,
                                                                  Path sourceImagesDir,
                                                                  Path targetImagesDir,
                                                                  Path targetAnnoDir,
                                                                  Path sourceAnnoDir,
                                                                  Map<String, String> mappingRule,
                                                                  Long projectId) throws IOException {
        log.info("处理标注文件: {}", annotationFile);

        List<String> lines = Files.readAllLines(annotationFile, StandardCharsets.UTF_8);
        List<String> newLines = new ArrayList<>();
        Set<String> currentKeptLabels = new LinkedHashSet<>();
        Set<String> currentRemovedLabels = new LinkedHashSet<>();
        Map<String, Integer> currentLabelStatistics = new HashMap<>();

        boolean hasValidAnnotations = false;

        // 处理每一行标注
        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                continue;
            }

            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 10) {
                String originalLabel = parts[parts.length - 2];

                // "或"关系逻辑：只要标签在映射规则中就保留
                if (mappingRule.containsKey(originalLabel)) {
                    // 转换为目标标签
                    String targetLabel = mappingRule.get(originalLabel);
                    parts[parts.length - 2] = targetLabel;
                    String newLine = String.join(" ", parts);
                    newLines.add(newLine);

                    currentKeptLabels.add(targetLabel);
                    currentLabelStatistics.put(targetLabel,
                            currentLabelStatistics.getOrDefault(targetLabel, 0) + 1);

                    hasValidAnnotations = true;
                } else if (mappingRule.isEmpty()) {
                    // 没有映射规则，保留所有
                    newLines.add(line);
                    currentKeptLabels.add(originalLabel);
                    currentLabelStatistics.put(originalLabel,
                            currentLabelStatistics.getOrDefault(originalLabel, 0) + 1);
                    hasValidAnnotations = true;
                } else {
                    // 有映射规则但不匹配，移除
                    currentRemovedLabels.add(originalLabel);
                }
            }
        }

        // 如果没有有效标注，跳过文件
        if (!hasValidAnnotations) {
            log.info("文件所有标注都被过滤，跳过: {}", annotationFile);
            return FilterResult.builder()
                    .keptImages(0)
                    .keptAnnotations(0)
                    .keptLabels(new LinkedHashSet<>())
                    .removedLabels(currentRemovedLabels)
                    .filtered(true)
                    .labelStatistics(currentLabelStatistics)
                    .build();
        }

        // 获取原始文件名（不含扩展名）
        String originalFileName = annotationFile.getFileName().toString();
        String originalBaseName = originalFileName.contains(".") ?
                originalFileName.substring(0, originalFileName.lastIndexOf('.')) : originalFileName;

        // 生成新文件名：projectId_原文件名
        String newBaseName = projectId + "_" + originalBaseName;

        // 写入重命名后的标注文件
        Path newAnnotationFile = targetAnnoDir.resolve(newBaseName + ".txt");
        Files.write(newAnnotationFile, newLines, StandardCharsets.UTF_8);
        log.info("写入重命名标注文件: {} -> {}", annotationFile.getFileName(), newAnnotationFile.getFileName());

        // 复制并重命名图片文件
        boolean imageCopied = copyAndRenameImageWithProjectId(sourceImagesDir, targetImagesDir,
                originalBaseName, newBaseName);

        log.info("文件处理完成 - 新文件名: {}, 保留标注: {}, 保留标签: {}",
                newBaseName, newLines.size(), currentKeptLabels);

        return FilterResult.builder()
                .keptImages(imageCopied ? 1 : 0)
                .keptAnnotations(newLines.size())
                .keptLabels(currentKeptLabels)
                .removedLabels(currentRemovedLabels)
                .filtered(!currentRemovedLabels.isEmpty())
                .labelStatistics(currentLabelStatistics)
                .build();
    }

    /**
     * 复制并重命名图片文件 - 使用 projectid_原文件名 格式
     */
    private boolean copyAndRenameImageWithProjectId(Path sourceImagesDir,
                                                    Path targetImagesDir,
                                                    String originalBaseName,
                                                    String newBaseName) throws IOException {
        if (sourceImagesDir == null || !Files.exists(sourceImagesDir)) {
            log.warn("源图片目录不存在: {}", sourceImagesDir);
            return false;
        }

        log.info("查找对应图片 - 原始: {}, 新名称: {}", originalBaseName, newBaseName);

        // 查找原始图片文件
        for (String ext : IMAGE_EXTENSIONS) {
            Path sourceImage = sourceImagesDir.resolve(originalBaseName + ext);

            if (Files.exists(sourceImage)) {
                // 使用新名称复制图片
                Path targetImage = targetImagesDir.resolve(newBaseName + ext);
                Files.copy(sourceImage, targetImage, StandardCopyOption.REPLACE_EXISTING);
                log.info("复制并重命名图片: {} -> {}", sourceImage.getFileName(), targetImage.getFileName());
                return true;
            }
        }

        log.warn("未找到对应图片: {}", originalBaseName);
        return false;
    }

    /**
     * 构建标签映射规则
     */
    private Map<String, String> buildMappingRule(List<TaskDatasetLabelMappingVO> mappings) {
        Map<String, String> mappingRule = new HashMap<>();

        if (CollUtil.isEmpty(mappings)) {
            return mappingRule;
        }

        for (TaskDatasetLabelMappingVO mapping : mappings) {
            String sourceLabel = mapping.getSourceLabel();
            String targetLabel = mapping.getTargetLabel();
            if (StrUtil.isNotBlank(sourceLabel) && StrUtil.isNotBlank(targetLabel)) {
                mappingRule.put(sourceLabel, targetLabel);
            }
        }

        return mappingRule;
    }

    /**
     * 合并标签统计
     */
    private void mergeLabelStatistics(Map<String, Integer> target, Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String label = entry.getKey();
            Integer count = entry.getValue();
            target.put(label, target.getOrDefault(label, 0) + count);
        }
    }

    private String getTaskDatasetRoot() {
        return Paths.get(taskDatasetRoot).toString();
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("创建目录失败: " + path, e);
        }
    }

    private void createSubsetStructure(Path subsetBase) {
        createDirectories(subsetBase.resolve(IMAGES_DIR));
        createDirectories(subsetBase.resolve(ANNOTATIONS_DIR));
    }

    @Data
    @Builder
    public static class FilterResult {
        private int keptImages;
        private int keptAnnotations;
        @Builder.Default
        private Set<String> keptLabels = new LinkedHashSet<>();
        @Builder.Default
        private Set<String> removedLabels = new LinkedHashSet<>();
        private boolean filtered;
        @Builder.Default
        private Map<String, Integer> labelStatistics = new HashMap<>();

        static FilterResult empty() {
            return FilterResult.builder().build();
        }

        /**
         * 获取标签统计的JSON字符串格式
         */
        public String getLabelStatisticsJson() {
            return JSONUtil.toJsonStr(labelStatistics);
        }
    }
}