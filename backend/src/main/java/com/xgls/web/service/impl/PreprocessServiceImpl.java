package com.xgls.web.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xgls.web.entity.*;
import com.xgls.web.mapper.*;
import com.xgls.web.service.PreprocessService;
import com.xgls.web.service.TaskDataset1Service;
import com.xgls.web.utils.InstanceDatasetPathUtil;
import com.xgls.web.utils.InstanceDatasetTrainTestRandomSplitUtil;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Comparator;

@Service
public class PreprocessServiceImpl implements PreprocessService {
    @Value("${sys.instancecfg.instancedata-root:data/instance_dataset/}")
    private String instanceDataRoot;

    @Value("${sys.instancecfg.python-path:/home/omen1/miniconda3/envs/platform/bin/python}")
    private String pythonExecutable;

    @Autowired
    private InstanceDatasetMidMapper instanceDatasetMidMapper;
    @Autowired
    private PreprocessScriptInfoMapper preprocessScriptInfoMapper;
    @Autowired
    private InstanceDatasetMapper instanceDatasetMapper;

    @Autowired
    private TaskDataset1Service taskDatasetService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<InstanceDataset> runPreprocess(
            List<Long> sourceInstanceIds,
            Integer enhanceScriptId,
            Map<String, Object> enhanceParams,
            Integer augmentScriptId,
            Map<String, Object> augmentParams,
            Double trainRatio) throws Exception {

        double effectiveTrainRatio = trainRatio == null ? 0.8D : trainRatio;
        if (effectiveTrainRatio < 0.01D || effectiveTrainRatio > 0.99D) {
            throw new IllegalArgumentException("训练集占比须在 0.01～0.99 之间");
        }

        // 1. 确保根目录存在
        Path rootPath = WorkspacePathUtil.instanceDatasetRoot().toAbsolutePath().normalize();
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
            System.out.println("【目录】自动创建根目录: " + rootPath);
        }

        if (sourceInstanceIds == null || sourceInstanceIds.isEmpty()) {
            throw new RuntimeException("源实例数据集ID列表不能为空");
        }

        // 脚本均为可选；null 或非正数表示不使用该脚本。
        PreprocessScriptInfo enhanceScript = findOptionalScript(enhanceScriptId, "增强");
        PreprocessScriptInfo augmentScript = findOptionalScript(augmentScriptId, "增广");

        List<InstanceDataset> results = new ArrayList<>();

        // 2. 串行处理每个源数据集
        for (Long sourceId : sourceInstanceIds) {
            // 2.1 查询源数据集（mid 表优先；兼容历史写入 instance_dataset 的 id）
            InstanceDatasetMid source = instanceDatasetMidMapper.selectById(sourceId);
            if (source == null) {
                InstanceDataset legacy = instanceDatasetMapper.selectById(sourceId);
                if (legacy != null && InstanceDatasetMidServiceImpl.isMidLikeLegacyInstanceDataset(legacy)) {
                    source = new InstanceDatasetMid();
                    BeanUtils.copyProperties(legacy, source);
                }
            }
            if (source == null) {
                throw new RuntimeException("源实例数据集不存在: ID=" + sourceId);
            }

            // 中间实例数据集是只读输入。所有 COCO 展开、随机划分都在临时副本中完成，
            // 不能向 data/instance_dataset_mid 写入 txt 或修改用户的导出数据。
            Path sourceTrainImgPath = resolveMidDatasetPath(source.getTrainImagePath());
            Path sourceTrainAnnoPath = resolveMidDatasetPath(source.getTrainAnnoPath());
            Path sourceTestImgPath = resolveMidDatasetPath(source.getTestImagePath());
            Path sourceTestAnnoPath = resolveMidDatasetPath(source.getTestAnnoPath());
            ensureSourcePairDirectories(sourceTrainImgPath, sourceTrainAnnoPath, "训练侧");

            // 2.2 落盘：{instancedata-root}/{任务数据集名}/{实例数据集名}/images|annotations/...
            String outputName = generateOutputName(source.getName());
            Path instanceRoot = WorkspacePathUtil.instanceDatasetRoot().toAbsolutePath().normalize();
            String taskDirSeg = InstanceDatasetPathUtil.safeFinalDatasetDirSegment(source.getFatherName());
            Path taskDir = instanceRoot.resolve(taskDirSeg).normalize();
            Files.createDirectories(taskDir);
            Path datasetOut = taskDir.resolve(outputName).normalize();

            Path outputTrainImgPath = datasetOut.resolve("images").resolve("train");
            Path outputTrainAnnoPath = datasetOut.resolve("annotations").resolve("train");
            Path outputTestImgPath = datasetOut.resolve("images").resolve("test");
            Path outputTestAnnoPath = datasetOut.resolve("annotations").resolve("test");

            Files.createDirectories(outputTrainImgPath);
            Files.createDirectories(outputTrainAnnoPath);
            Files.createDirectories(outputTestImgPath);
            Files.createDirectories(outputTestAnnoPath);

            // 先将原图及标注复制到临时区域并划分。这样一张原图及其所有后续增广副本只会进入
            // 训练集或测试集其中之一，不会因“先增广、后随机划分”泄漏到另一侧。
            Path splitTempRoot = taskDir.resolve("temp_split_" + UUID.randomUUID());
            Path splitTrainImgPath = splitTempRoot.resolve("images").resolve("train");
            Path splitTrainAnnoPath = splitTempRoot.resolve("annotations").resolve("train");
            Path splitTestImgPath = splitTempRoot.resolve("images").resolve("test");
            Path splitTestAnnoPath = splitTempRoot.resolve("annotations").resolve("test");
            Files.createDirectories(splitTrainImgPath);
            Files.createDirectories(splitTrainAnnoPath);
            Files.createDirectories(splitTestImgPath);
            Files.createDirectories(splitTestAnnoPath);

            String outputTrainImgPathStr = pathWithTrailingSlash(outputTrainImgPath);
            String outputTrainAnnoPathStr = pathWithTrailingSlash(outputTrainAnnoPath);
            String outputTestImgPathStr = pathWithTrailingSlash(outputTestImgPath);
            String outputTestAnnoPathStr = pathWithTrailingSlash(outputTestAnnoPath);

            List<String> enhanceArgs = enhanceScript == null
                    ? Collections.emptyList() : buildScriptArgs(enhanceScript, enhanceParams);
            List<String> augmentArgs = augmentScript == null
                    ? Collections.emptyList() : buildScriptArgs(augmentScript, augmentParams);
            try {
                copyDirectory(sourceTrainImgPath.toString(), splitTrainImgPath.toString());
                copyDirectory(sourceTrainAnnoPath.toString(), splitTrainAnnoPath.toString());
                boolean hasSeparateSourceTest = !sameDirectory(sourceTrainImgPath, sourceTestImgPath)
                        || !sameDirectory(sourceTrainAnnoPath, sourceTestAnnoPath);
                if (hasSeparateSourceTest && isDirectory(sourceTestImgPath) && isDirectory(sourceTestAnnoPath)) {
                    copyDirectory(sourceTestImgPath.toString(), splitTestImgPath.toString());
                    copyDirectory(sourceTestAnnoPath.toString(), splitTestAnnoPath.toString());
                }
                materializeCocoAnnotations(splitTrainImgPath, splitTrainAnnoPath);
                materializeCocoAnnotations(splitTestImgPath, splitTestAnnoPath);
                Files.deleteIfExists(splitTrainAnnoPath.resolve("instances.json"));
                Files.deleteIfExists(splitTestAnnoPath.resolve("instances.json"));

                InstanceDataset splitSource = buildDiskDataset(source, outputName,
                        splitTrainImgPath, splitTrainAnnoPath, splitTestImgPath, splitTestAnnoPath, instanceRoot);
                InstanceDatasetTrainTestRandomSplitUtil.SplitResult sourceSplit =
                        InstanceDatasetTrainTestRandomSplitUtil.run(splitSource, instanceRoot.toString(), effectiveTrainRatio);

                processTrainSet(splitTrainImgPath, splitTrainAnnoPath,
                        outputTrainImgPath, outputTrainAnnoPath,
                        enhanceScript, enhanceArgs, augmentScript, augmentArgs, taskDir);
                processTestSet(splitTestImgPath, splitTestAnnoPath,
                        outputTestImgPath, outputTestAnnoPath, enhanceScript, enhanceArgs);

                materializeCocoAnnotations(outputTrainImgPath, outputTrainAnnoPath);
                materializeCocoAnnotations(outputTestImgPath, outputTestAnnoPath);
                Files.deleteIfExists(outputTrainAnnoPath.resolve("instances.json"));
                Files.deleteIfExists(outputTestAnnoPath.resolve("instances.json"));
                validatePairedDataset(outputTrainImgPath, outputTrainAnnoPath, "训练集");
                validatePairedDataset(outputTestImgPath, outputTestAnnoPath, "测试集");

                int finalTrainImages = InstanceDatasetTrainTestRandomSplitUtil.countImages(outputTrainImgPath);
                int finalTestImages = InstanceDatasetTrainTestRandomSplitUtil.countImages(outputTestImgPath);
                int trainAnnoCount = InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(outputTrainAnnoPath);
                int testAnnoCount = InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(outputTestAnnoPath);

                InstanceDataset result = buildDiskDataset(source, outputName,
                        outputTrainImgPath, outputTrainAnnoPath, outputTestImgPath, outputTestAnnoPath, instanceRoot);
                result.setImgNum(finalTrainImages + finalTestImages);
                result.setAnnoNum(trainAnnoCount + testAnnoCount);

                // 只记录实际执行的脚本；未使用脚本时保存空链路 []。
                List<Map<String, Object>> configList = new ArrayList<>();
                if (enhanceScript != null) {
                    configList.add(scriptConfig(configList.size() + 1, enhanceScript.getName()));
                }
                if (augmentScript != null) {
                    configList.add(scriptConfig(configList.size() + 1, augmentScript.getName()));
                }
                result.setConfigList(objectMapper.writeValueAsString(configList));

                writeUltralyticsDataYaml(datasetOut, result);

                Map<String, Object> allParams = new HashMap<>();
                allParams.put("enhance", enhanceParams);
                allParams.put("augment", augmentParams);
                allParams.put("trainRatio", effectiveTrainRatio);
                allParams.put("splitBeforePreprocess", true);
                allParams.put("sourceTrainImages", sourceSplit.trainImages());
                allParams.put("sourceTestImages", sourceSplit.testImages());
                result.setParamSchema(objectMapper.writeValueAsString(allParams));

                instanceDatasetMapper.insert(result);
                results.add(result);
            } catch (Exception e) {
                deleteDirectory(datasetOut.toString());
                throw e;
            } finally {
                deleteDirectory(splitTempRoot.toString());
            }
        }

        return results;
    }

    private Path resolveMidDatasetPath(String rawPath) {
        if (StrUtil.isBlank(rawPath)) return null;
        Path path = Paths.get(rawPath).normalize();
        if (path.isAbsolute()) return path;
        return WorkspacePathUtil.instanceDatasetMidRoot().resolve(path).normalize();
    }

    private void ensureSourcePairDirectories(Path images, Path annotations, String side) {
        if (!isDirectory(images) || !isDirectory(annotations)) {
            throw new IllegalStateException("中间实例数据集" + side + "的图片或标注目录不存在");
        }
    }

    private boolean isDirectory(Path path) {
        return path != null && Files.isDirectory(path);
    }

    private boolean sameDirectory(Path a, Path b) {
        return a != null && b != null && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private InstanceDataset buildDiskDataset(
            InstanceDatasetMid source,
            String outputName,
            Path trainImages,
            Path trainAnnotations,
            Path testImages,
            Path testAnnotations,
            Path instanceRoot) {
        InstanceDataset result = new InstanceDataset();
        result.setFatherName(source.getFatherName());
        result.setName(outputName);
        result.setSensorType(source.getSensorType());
        result.setTargetType(source.getTargetType());
        result.setClassNum(source.getClassNum());
        result.setClassList(source.getClassList());
        applyTaskTargetSchemaClassList(result);
        result.setTrainImagePath(pathWithTrailingSlash(instanceRoot, trainImages));
        result.setTrainAnnoPath(pathWithTrailingSlash(instanceRoot, trainAnnotations));
        result.setTestImagePath(pathWithTrailingSlash(instanceRoot, testImages));
        result.setTestAnnoPath(pathWithTrailingSlash(instanceRoot, testAnnotations));
        result.setDataFormat(source.getDataFormat());
        result.setUsername(source.getUsername());
        result.setCreatedTime(LocalDateTime.now());
        result.setUpdatedTime(LocalDateTime.now());
        return result;
    }

    private void processTrainSet(
            Path inputImages, Path inputAnnotations,
            Path outputImages, Path outputAnnotations,
            PreprocessScriptInfo enhanceScript, List<String> enhanceArgs,
            PreprocessScriptInfo augmentScript, List<String> augmentArgs,
            Path taskDir) throws Exception {
        if (enhanceScript != null && augmentScript != null) {
            Path tempRoot = taskDir.resolve("temp_preprocess_" + UUID.randomUUID());
            Path tempImages = tempRoot.resolve("images");
            Path tempAnnotations = tempRoot.resolve("annotations");
            Files.createDirectories(tempImages);
            Files.createDirectories(tempAnnotations);
            try {
                runPython(enhanceScript.getScript_path(), inputImages.toString(), inputAnnotations.toString(),
                        tempImages.toString(), tempAnnotations.toString(), enhanceArgs.toArray(new String[0]));
                runPython(augmentScript.getScript_path(), tempImages.toString(), tempAnnotations.toString(),
                        outputImages.toString(), outputAnnotations.toString(), augmentArgs.toArray(new String[0]));
            } finally {
                deleteDirectory(tempRoot.toString());
            }
        } else if (enhanceScript != null) {
            runPython(enhanceScript.getScript_path(), inputImages.toString(), inputAnnotations.toString(),
                    outputImages.toString(), outputAnnotations.toString(), enhanceArgs.toArray(new String[0]));
        } else if (augmentScript != null) {
            runPython(augmentScript.getScript_path(), inputImages.toString(), inputAnnotations.toString(),
                    outputImages.toString(), outputAnnotations.toString(), augmentArgs.toArray(new String[0]));
        } else {
            copyDirectory(inputImages.toString(), outputImages.toString());
            copyDirectory(inputAnnotations.toString(), outputAnnotations.toString());
        }
    }

    /** 测试集禁止增广；增强脚本必须是不会改变标签语义的确定性处理。 */
    private void processTestSet(
            Path inputImages, Path inputAnnotations,
            Path outputImages, Path outputAnnotations,
            PreprocessScriptInfo enhanceScript, List<String> enhanceArgs) throws Exception {
        if (enhanceScript != null) {
            runPython(enhanceScript.getScript_path(), inputImages.toString(), inputAnnotations.toString(),
                    outputImages.toString(), outputAnnotations.toString(), enhanceArgs.toArray(new String[0]));
        } else {
            copyDirectory(inputImages.toString(), outputImages.toString());
            copyDirectory(inputAnnotations.toString(), outputAnnotations.toString());
        }
    }

    private void validatePairedDataset(Path imagesDir, Path annotationsDir, String side) throws IOException {
        int images = 0;
        try (var walk = Files.walk(imagesDir)) {
            for (Path image : walk.filter(Files::isRegularFile).toList()) {
                String name = image.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                        || lower.endsWith(".bmp") || lower.endsWith(".tif") || lower.endsWith(".tiff"))) {
                    continue;
                }
                images++;
                Path rel = imagesDir.relativize(image);
                String fileName = rel.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
                Path parent = rel.getParent() == null ? annotationsDir : annotationsDir.resolve(rel.getParent());
                if (!Files.isRegularFile(parent.resolve(stem + ".txt"))
                        && !Files.isRegularFile(parent.resolve(stem + ".json"))
                        && !Files.isRegularFile(parent.resolve(stem + ".xml"))) {
                    throw new IllegalStateException(side + "存在图片但缺少同名标注: " + rel);
                }
            }
        }
        if (images < 1) {
            throw new IllegalStateException(side + "没有可用图片");
        }
    }

    /** Write a portable Ultralytics dataset description beside the processed dataset. */
    private void writeUltralyticsDataYaml(Path datasetOut, InstanceDataset dataset) throws IOException {
        List<String> classNames = classNames(dataset.getClassList());
        if (classNames.isEmpty()) {
            throw new IllegalStateException("预处理结果没有可用于生成 data.yaml 的类别");
        }
        StringBuilder yaml = new StringBuilder();
        yaml.append("path: .\n");
        yaml.append("train: images/train\n");
        yaml.append("val: images/test\n");
        yaml.append("names:\n");
        for (int i = 0; i < classNames.size(); i++) {
            yaml.append("  ").append(i).append(": ").append(yamlScalar(classNames.get(i))).append('\n');
        }
        Files.writeString(datasetOut.resolve("data.yaml"), yaml.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private List<String> classNames(String rawClassList) {
        if (StrUtil.isBlank(rawClassList)) return Collections.emptyList();
        try {
            Object parsed = JSONUtil.parse(rawClassList);
            if (parsed instanceof JSONObject object) {
                return new ArrayList<>(object.keySet());
            }
            if (parsed instanceof JSONArray array) {
                List<String> names = new ArrayList<>();
                for (Object value : array) {
                    String name = StrUtil.trimToEmpty(String.valueOf(value));
                    if (StrUtil.isNotBlank(name)) names.add(name);
                }
                return names;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private String yamlScalar(String value) {
        String escaped = StrUtil.nullToEmpty(value).replace("'", "''");
        return "'" + escaped + "'";
    }

    private void materializeStoredMidCoco(String imagePath, String annotationPath) throws IOException {
        if (StrUtil.isBlank(imagePath) || StrUtil.isBlank(annotationPath)) return;
        Path images = Paths.get(imagePath).normalize();
        Path annotations = Paths.get(annotationPath).normalize();
        Path midRoot = WorkspacePathUtil.instanceDatasetMidRoot().toAbsolutePath().normalize();
        if (!images.isAbsolute()) images = midRoot.resolve(images).normalize();
        if (!annotations.isAbsolute()) annotations = midRoot.resolve(annotations).normalize();
        if (Files.isDirectory(images) && Files.isDirectory(annotations)) {
            materializeCocoAnnotations(images, annotations);
        }
    }

    /** 兼容历史中间集：将集中式 COCO instances.json 展开为逐图片同名 DOTA TXT。 */
    private void materializeCocoAnnotations(Path imagesDir, Path annotationsDir) throws IOException {
        Path coco = annotationsDir.resolve("instances.json");
        if (!Files.isRegularFile(coco)) return;
        JSONObject root = JSONUtil.parseObj(Files.readString(coco, StandardCharsets.UTF_8));
        JSONArray images = root.getJSONArray("images");
        JSONArray annotations = root.getJSONArray("annotations");
        JSONArray categories = root.getJSONArray("categories");
        if (images == null || annotations == null || categories == null) return;

        Map<Integer, String> categoryNames = new HashMap<>();
        for (Object value : categories) {
            if (value instanceof JSONObject category && category.getInt("id") != null) {
                categoryNames.put(category.getInt("id"), category.getStr("name", ""));
            }
        }
        Map<Long, String> imageNames = new HashMap<>();
        Set<String> imagesWithExistingTxt = new HashSet<>();
        for (Object value : images) {
            if (value instanceof JSONObject image && image.getLong("id") != null) {
                String fileName = image.getStr("file_name", "");
                imageNames.put(image.getLong("id"), fileName);
                int dot = fileName.lastIndexOf('.');
                String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
                if (Files.isRegularFile(annotationsDir.resolve(stem + ".txt"))) imagesWithExistingTxt.add(fileName);
            }
        }
        for (Object value : annotations) {
            if (!(value instanceof JSONObject annotation)) continue;
            String imageName = imageNames.get(annotation.getLong("image_id"));
            String category = categoryNames.get(annotation.getInt("category_id"));
            if (StrUtil.isBlank(imageName) || StrUtil.isBlank(category)) continue;
            if (imagesWithExistingTxt.contains(imageName)) continue;
            Path image = imagesDir.resolve(imageName).normalize();
            if (!image.startsWith(imagesDir.normalize()) || !Files.isRegularFile(image)) continue;
            List<Double> polygon = cocoPolygon(annotation);
            if (polygon.size() < 8) continue;
            int dot = imageName.lastIndexOf('.');
            String stem = dot > 0 ? imageName.substring(0, dot) : imageName;
            Path txt = annotationsDir.resolve(stem + ".txt");
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) line.append(' ');
                line.append(polygon.get(i));
            }
            line.append(' ').append(category).append(" 0\n");
            Files.writeString(txt, line.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private List<Double> cocoPolygon(JSONObject annotation) {
        JSONArray segmentation = annotation.getJSONArray("segmentation");
        if (segmentation != null && !segmentation.isEmpty() && segmentation.get(0) instanceof JSONArray arr
                && arr.size() >= 8) {
            List<Double> points = new ArrayList<>();
            for (int i = 0; i < 8; i++) points.add(arr.getDouble(i));
            return points;
        }
        JSONArray bbox = annotation.getJSONArray("bbox");
        if (bbox == null || bbox.size() < 4) return Collections.emptyList();
        double x = bbox.getDouble(0, 0D), y = bbox.getDouble(1, 0D);
        double w = bbox.getDouble(2, 0D), h = bbox.getDouble(3, 0D);
        return List.of(x, y, x + w, y, x + w, y + h, x, y + h);
    }

    private PreprocessScriptInfo findOptionalScript(Integer scriptId, String scriptType) {
        if (scriptId == null || scriptId <= 0) {
            return null;
        }
        PreprocessScriptInfo script = preprocessScriptInfoMapper.selectById(scriptId);
        if (script == null) {
            throw new RuntimeException(scriptType + "脚本不存在: ID=" + scriptId);
        }
        return script;
    }

    private Map<String, Object> scriptConfig(int order, String name) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("order", order);
        config.put("name", name);
        return config;
    }

    /**
     * 与任务数据集管理中配置的 target_schema 对齐类别名与顺序；数量来自中间实例数据集（导出统计），键名按任务定义归一匹配。
     */
    private void applyTaskTargetSchemaClassList(InstanceDataset result) {
        String taskName = result.getFatherName();
        if (StrUtil.isBlank(taskName)) {
            return;
        }
        TaskDataset task = taskDatasetService.lambdaQuery()
                .eq(TaskDataset::getName, taskName.trim())
                .orderByDesc(TaskDataset::getId)
                .last("limit 1")
                .one();
        if (task == null || StrUtil.isBlank(task.getTargetSchema())) {
            return;
        }
        JSONArray schema;
        try {
            schema = JSONUtil.parseArray(task.getTargetSchema());
        } catch (Exception e) {
            return;
        }
        if (schema == null || schema.isEmpty()) {
            return;
        }
        JSONObject midCounts;
        try {
            midCounts = JSONUtil.parseObj(StrUtil.blankToDefault(result.getClassList(), "{}"));
        } catch (Exception e) {
            midCounts = new JSONObject();
        }
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        for (Object t : schema) {
            String targetLabel = StrUtil.trimToEmpty(String.valueOf(t));
            if (StrUtil.isBlank(targetLabel)) {
                continue;
            }
            long cnt = 0L;
            for (String k : midCounts.keySet()) {
                if (normalizeClassKey(k).equals(normalizeClassKey(targetLabel))) {
                    Object raw = midCounts.get(k);
                    if (raw instanceof Number) {
                        cnt = ((Number) raw).longValue();
                    }
                    break;
                }
            }
            out.put(targetLabel, cnt);
        }
        if (!out.isEmpty()) {
            result.setClassList(JSONUtil.toJsonStr(out));
            result.setClassNum(out.size());
        }
    }

    private static String normalizeClassKey(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    /** 数据库保存相对 instance_dataset 根目录的 POSIX 路径，避免绑定某台机器的绝对路径。 */
    private static String pathWithTrailingSlash(Path root, Path p) {
        Path rel = root.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize());
        return pathWithTrailingSlash(rel);
    }
    /** 与历史库中记录风格一致：POSIX 路径且以 / 结尾，便于前端与训练侧展示 */
    private static String pathWithTrailingSlash(Path p) {
        String s = p.normalize().toString().replace("\\", "/");
        return s.endsWith("/") ? s : s + "/";
    }

    // 自动生成带时间戳的名称
    String generateOutputName(String sourceName) {
        String[] parts = sourceName.split("_");
        if (parts.length >= 3) {
            String maybeTimestamp = parts[parts.length - 2];
            if (maybeTimestamp.matches("\\d{14}")) {
                String prefix = String.join("_", Arrays.copyOfRange(parts, 0, parts.length - 2));
                String suffix = parts[parts.length - 1];
                String newTimestamp = LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                );
                return prefix + "_" + newTimestamp + "_" + suffix;
            }
        }
        String newTimestamp = LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        );
        return sourceName + "_" + newTimestamp;
    }

    private List<String> buildScriptArgs(PreprocessScriptInfo script, Map<String, Object> userParams) {
        List<String> args = new ArrayList<>();
        String paramSchemaJson = script.getParamSchema();
        if (paramSchemaJson == null || paramSchemaJson.trim().isEmpty() || "[]".equals(paramSchemaJson.trim())) {
            return args;
        }
        try {
            List<ParamDef> paramDefs = objectMapper.readValue(paramSchemaJson, new TypeReference<List<ParamDef>>() {});
            for (ParamDef def : paramDefs) {
                Object value = userParams != null ? userParams.get(def.getName()) : null;
                if (Boolean.TRUE.equals(def.getRequired()) && value == null) {
                    throw new RuntimeException("缺少必填参数: " + (def.getLabel() != null ? def.getLabel() : def.getName()));
                }
                if (value == null) {
                    value = def.getDefaultValue();
                }
                args.add(value != null ? value.toString() : "");
            }
        } catch (Exception e) {
            throw new RuntimeException("脚本参数解析失败: " + e.getMessage(), e);
        }
        return args;
    }

    private void runPython(String scriptPath, String imgIn, String lblIn, String imgOut, String lblOut, String... extraArgs) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(scriptPath);
        command.add(imgIn);
        command.add(lblIn);
        command.add(imgOut);
        command.add(lblOut);
        command.addAll(Arrays.asList(extraArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Python] " + line);
            }
        }

        int code = process.waitFor();
        if (code != 0) {
            throw new RuntimeException("Python执行失败 code=" + code);
        }
    }

    // ✅ 新增：安全删除目录（含子目录和文件）
    private void deleteDirectory(String dirPath) {
        try {
            Path dir = Paths.get(dirPath);
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder()) // 先删文件，再删目录
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("【清理】已删除临时目录: " + dirPath);
            }
        } catch (IOException e) {
            System.err.println("【警告】无法删除临时目录: " + dirPath + " - " + e.getMessage());
            // 不抛异常，避免中断主流程
        }
    }

    // 未选择相应脚本时，原样复制图片或标注目录。
    private void copyDirectory(String sourceDir, String targetDir) throws IOException {
        if (StrUtil.isBlank(sourceDir)) {
            return;
        }
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);

        if (!Files.exists(sourcePath)) {
            System.out.println("⚠️ 警告：源目录不存在，跳过复制: " + sourceDir);
            return;
        }

        Files.walk(sourcePath).forEach(source -> {
            Path target = targetPath.resolve(sourcePath.relativize(source));
            try {

                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("复制目录失败: " + source + " → " + target, e);
            }
        });
    }

    // 内部类保持不变
    public static class ParamDef {
        private String name;
        private String label;
        private String type;
        private Boolean required;
        private Object defaultValue;
        private Double min;
        private Double max;
        private List<String> options;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
        public Double getMin() { return min; }
        public void setMin(Double min) { this.min = min; }
        public Double getMax() { return max; }
        public void setMax(Double max) { this.max = max; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
    }
}
