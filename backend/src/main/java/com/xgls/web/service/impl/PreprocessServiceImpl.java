package com.xgls.web.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.web.entity.*;
import com.xgls.web.mapper.*;
import com.xgls.web.service.PreprocessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Comparator;

@Service
public class PreprocessServiceImpl implements PreprocessService {
    @Value("${sys.instancecfg.instancedata-root:/home/omen1/AI_TT_Platform/data/instance_dataset/}")
    private String instanceDataRoot;

    @Value("${sys.instancecfg.python-path:/home/omen1/miniconda3/envs/platform/bin/python}")
    private String pythonExecutable;

    @Autowired
    private InstanceDatasetMapper instanceDatasetMapper;
    @Autowired
    private PreprocessScriptInfoMapper preprocessScriptInfoMapper;
    @Autowired
    private InstanceDatasetinfoMapper instanceDatasetinfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<InstanceDatasetinfo> runPreprocess(
            List<Long> sourceInstanceIds,
            Integer enhanceScriptId,
            Map<String, Object> enhanceParams,
            Integer augmentScriptId,
            Map<String, Object> augmentParams) throws Exception {

        // 1. 确保根目录存在
        Path rootPath = Paths.get(instanceDataRoot);
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
            System.out.println("【目录】自动创建根目录: " + instanceDataRoot);
        }

        if (sourceInstanceIds == null || sourceInstanceIds.isEmpty()) {
            throw new RuntimeException("源实例数据集ID列表不能为空");
        }

        // 1. 查询脚本（只需查一次）
        PreprocessScriptInfo enhanceScript = preprocessScriptInfoMapper.selectById(enhanceScriptId);
        PreprocessScriptInfo augmentScript = preprocessScriptInfoMapper.selectById(augmentScriptId);
        if (enhanceScript == null || augmentScript == null) {
            throw new RuntimeException("增强或增广脚本不存在");
        }

        List<InstanceDatasetinfo> results = new ArrayList<>();

        // 2. 串行处理每个源数据集
        for (Long sourceId : sourceInstanceIds) {
            // 2.1 查询源数据集
            InstanceDataset source = instanceDatasetMapper.selectById(sourceId);
            if (source == null) {
                throw new RuntimeException("源实例数据集不存在: ID=" + sourceId);
            }

            // 2.2 自动生成唯一输出名称（Path 拼接，避免根目录漏写末尾 / 时路径粘连错误）
            String outputName = generateOutputName(source.getName());
            Path instanceRoot = Paths.get(instanceDataRoot.trim().replaceAll("/+$", ""));
            Path datasetOut = instanceRoot.resolve(outputName);

            Path outputTrainImgPath = datasetOut.resolve("images").resolve("train");
            Path outputTrainAnnoPath = datasetOut.resolve("annotations").resolve("train");
            Path outputTestImgPath = datasetOut.resolve("images").resolve("test");
            Path outputTestAnnoPath = datasetOut.resolve("annotations").resolve("test");

            Files.createDirectories(outputTrainImgPath);
            Files.createDirectories(outputTrainAnnoPath);
            Files.createDirectories(outputTestImgPath);
            Files.createDirectories(outputTestAnnoPath);

            String outputTrainImgPathStr = pathWithTrailingSlash(outputTrainImgPath);
            String outputTrainAnnoPathStr = pathWithTrailingSlash(outputTrainAnnoPath);
            String outputTestImgPathStr = pathWithTrailingSlash(outputTestImgPath);
            String outputTestAnnoPathStr = pathWithTrailingSlash(outputTestAnnoPath);

            // 2.3 构建脚本参数
            List<String> enhanceArgs = buildScriptArgs(enhanceScript, enhanceParams);
            List<String> augmentArgs = buildScriptArgs(augmentScript, augmentParams);

            // ===========================================
            // ✅【关键修改】为训练集创建临时中间目录
            // ===========================================
            Path tempRoot = instanceRoot.resolve("temp_preprocess_" + UUID.randomUUID());
            Path tempEnhancedImgPath = tempRoot.resolve("images");
            Path tempEnhancedAnnoPath = tempRoot.resolve("annotations");

            Files.createDirectories(tempEnhancedImgPath);
            Files.createDirectories(tempEnhancedAnnoPath);
            String tempRootStr = pathWithTrailingSlash(tempRoot);
            String tempEnhancedImgPathStr = pathWithTrailingSlash(tempEnhancedImgPath);
            String tempEnhancedAnnoPathStr = pathWithTrailingSlash(tempEnhancedAnnoPath);

            try {
                // 2.4 对训练集：先增强 → 到临时目录
                System.out.println("【训练集】增强处理 → 临时目录: " + source.getName());
                runPython(enhanceScript.getScript_path(),
                        source.getTrainImagePath(),
                        source.getTrainAnnoPath(),
                        tempEnhancedImgPathStr,
                        tempEnhancedAnnoPathStr,
                        enhanceArgs.toArray(new String[0])
                );

                // 2.5 对训练集：再增广 → 从临时目录读，写入最终目录
                System.out.println("【训练集】增广处理 → 最终目录: " + outputName);
                runPython(augmentScript.getScript_path(),
                        tempEnhancedImgPathStr,
                        tempEnhancedAnnoPathStr,
                        outputTrainImgPathStr,
                        outputTrainAnnoPathStr,
                        augmentArgs.toArray(new String[0])
                );

            } finally {
                // 2.6 清理临时目录（即使出错也清理）
                deleteDirectory(tempRootStr);
            }

            // ===========================================
            // ✅ 测试集：仅增强（不增广），输出到 test 目录
            // ===========================================
            if (source.getTestImagePath() != null && !source.getTestImagePath().isEmpty()) {
                System.out.println("【测试集】增强处理: " + source.getName());
                runPython(enhanceScript.getScript_path(),
                        source.getTestImagePath(),
                        source.getTestAnnoPath(),
                        outputTestImgPathStr,
                        outputTestAnnoPathStr,
                        enhanceArgs.toArray(new String[0])
                );
            } else {
                // 如果没有测试集，创建空目录（保持结构一致）
                Files.createDirectories(outputTestImgPath);
                Files.createDirectories(outputTestAnnoPath);
            }

            // 2.7 保存结果记录
            InstanceDatasetinfo result = new InstanceDatasetinfo();
            result.setFatherName(source.getFatherName());
            result.setName(outputName);
            result.setSensorType(source.getSensorType());
            result.setTargetType(source.getTargetType());
            result.setImgNum(source.getImgNum());
            result.setAnnoNum(source.getAnnoNum());
            result.setClassNum(source.getClassNum());
            result.setClassList(source.getClassList());
            result.setTrainImagePath(outputTrainImgPathStr);
            result.setTrainAnnoPath(outputTrainAnnoPathStr);
            result.setTestImagePath(outputTestImgPathStr);
            result.setTestAnnoPath(outputTestAnnoPathStr);
            result.setDataFormat(source.getDataFormat());
            result.setUsername(source.getUsername());
            result.setCreatedTime(LocalDateTime.now());
            result.setUpdatedTime(LocalDateTime.now());

            // 预处理链路
            result.setConfigList(String.format(
                    "[{\"order\":1,\"name\":\"%s\"},{\"order\":2,\"name\":\"%s\"}]",
                    enhanceScript.getName(),
                    augmentScript.getName()
            ));

            // 实际参数记录
            Map<String, Object> allParams = new HashMap<>();
            allParams.put("enhance", enhanceParams);
            allParams.put("augment", augmentParams);
            result.setParamSchema(objectMapper.writeValueAsString(allParams));

            instanceDatasetinfoMapper.insert(result);
            results.add(result);
        }

        return results;
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

    // 原 copyDirectory 方法保留（虽然当前未使用，但可备用于未来）
    private void copyDirectory(String sourceDir, String targetDir) throws IOException {
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