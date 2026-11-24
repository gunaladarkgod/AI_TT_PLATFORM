package com.xgls.web.service;

import cn.hutool.json.*;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Reviewed platform adapters. Downloaded repository contents never define commands. */
@Service
public class ComponentTrainingService {
    private final AlgorithmComponentService components;
    private final Path root;
    private final CustomAlgorithmService custom;

    @Autowired
    public ComponentTrainingService(AlgorithmComponentService components, CustomAlgorithmService custom) {
        this(components, custom, WorkspacePathUtil.workspaceRoot());
    }

    ComponentTrainingService(AlgorithmComponentService components, Path root) {
        this(components, new CustomAlgorithmService(root), root);
    }

    private ComponentTrainingService(AlgorithmComponentService components, CustomAlgorithmService custom, Path root) {
        this.components = components;
        this.custom = custom;
        this.root = root.toAbsolutePath().normalize();
    }
    public List<JSONObject> list(String direction) {
        try {
            List<JSONObject> result = new ArrayList<>();
            byte[] configuration = Files.readAllBytes(root.resolve("research-registry/training-adapters.json"));
            var adapters = JSONUtil.parseArray(new String(configuration, java.nio.charset.StandardCharsets.UTF_8));
            JSONObject localTemplate = null;
            for (Object item : adapters) {
                JSONObject adapter = JSONUtil.parseObj(item);
                if ("small-object/yolo11".equals(adapter.getStr("id")) && "baseline".equals(adapter.getStr("kind"))) {
                    localTemplate = adapter;
                    break;
                }
            }
            if (localTemplate != null) {
                for (var metadata : custom.list(direction)) result.add(custom.trainingOption(metadata, localTemplate, configuration));
            }
            for (String kind : List.of("baseline", "algorithm")) {
                Object rows = components.list(kind, direction).get("components");
                if (!(rows instanceof Collection<?> entries)) continue;
                for (Object entry : entries) {
                    JSONObject component = JSONUtil.parseObj(entry);
                    if (!"downloaded".equals(component.getStr("localStatus"))) continue;
                    JSONObject adapter = null;
                    for (Object candidate : adapters) {
                        JSONObject definition = JSONUtil.parseObj(candidate);
                        if (kind.equals(definition.getStr("kind")) && component.getStr("id").equals(definition.getStr("id"))) { adapter = definition; break; }
                    }
                    if (adapter == null) {
                        JSONObject unavailable = new JSONObject(component);
                        unavailable.set("name", component.getStr("name") + ("baseline".equals(kind) ? "（baseline）" : ""));
                        unavailable.set("valid", false); unavailable.set("blockers", List.of("尚未配置审核后的运行适配器"));
                        unavailable.set("data_format", component.getStr("dataFormat"));
                        result.add(unavailable); continue;
                    }
                    Path path = root.resolve(component.getStr("path")).normalize();
                    List<String> errors = new ArrayList<>();
                    if (!path.toRealPath().startsWith(root.resolve("local-components").toRealPath())) errors.add("组件路径超出许可目录");
                    if (!Files.isRegularFile(path.resolve("ultralytics/__init__.py"))) errors.add("缺少算法源码");
                    String commit = revision(path);
                    byte[] localConfiguration = savedConfigurationBytes(kind, component.getStr("id"));
                    JSONObject manual = savedConfiguration(kind, component.getStr("id"), commit);
                    if (manual != null) {
                        adapter.set("model", manual.getStr("model"));
                        adapter.set("pretrained", manual.getStr("pretrained"));
                    } else if (localConfiguration.length > 0) {
                        errors.add("组件版本已变化，请重新保存训练配置");
                    }
                    String modelValue = adapter.getStr("model", "");
                    if (modelValue.toLowerCase(Locale.ROOT).endsWith(".yaml") || modelValue.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                        Path modelPath = path.resolve(modelValue).normalize();
                        if (!modelPath.startsWith(path) || !Files.isRegularFile(modelPath)) errors.add("缺少模型配置");
                    }
                    Process diff = new ProcessBuilder("git", "-c", "safe.directory=" + path.toString().replace("\\", "/"), "diff", "--quiet", "HEAD", "--").directory(path.toFile()).start();
                    if (!diff.waitFor(5, TimeUnit.SECONDS)) { diff.destroyForcibly(); errors.add("组件变更检查超时"); }
                    else if (diff.exitValue() != 0) errors.add("组件含未提交源码变更，请先提交后使用");
                    if (commit.isBlank()) errors.add("无法读取组件版本");
                    adapter.set("component_commit", commit);
                    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                    digest.update(configuration);
                    digest.update(localConfiguration);
                    adapter.set("adapter_digest", java.util.HexFormat.of().formatHex(digest.digest()));
                    adapter.set("valid", errors.isEmpty());
                    adapter.set("blockers", errors);
                    result.add(adapter);
                }
            }
            return result;
        } catch (Exception e) { throw new IllegalArgumentException("训练方案读取失败：" + e.getMessage(), e); }
    }
    public JSONObject require(String id, String direction) {
        JSONObject item = list(direction).stream().filter(row -> id.equals(row.getStr("id"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("方案尚未下载或不属于所选方向，请刷新算法列表"));
        if (!item.getBool("valid", false)) throw new IllegalArgumentException("方案不可用：" + item.get("blockers"));
        return item;
    }
    public void validateParameters(JSONObject adapter, JSONObject params) {
        JSONObject values = params.getJSONObject("ultralytics_parameters");
        if (values == null) values = new JSONObject();
        Set<String> keys = new HashSet<>();
        for (Object raw : adapter.getJSONArray("parameters")) {
            JSONObject rule = JSONUtil.parseObj(raw);
            String key = rule.getStr("key"); keys.add(key);
            Object value = values.getOrDefault(key, rule.get("default"));
            String type = rule.getStr("type");
            if ("integer".equals(type) || "number".equals(type)) {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() < rule.getDouble("min") || number.doubleValue() > rule.getDouble("max")
                    || ("integer".equals(type) && number.doubleValue() != Math.rint(number.doubleValue())))
                    throw new IllegalArgumentException("训练参数类型或范围错误：" + key);
            } else if ("boolean".equals(type) && !(value instanceof Boolean)
                    || "string".equals(type) && (!(value instanceof String) || ((String)value).length() > 100)) {
                throw new IllegalArgumentException("训练参数类型错误：" + key);
            }
            values.set(key, value);
        }
        if (!keys.containsAll(values.keySet())) throw new IllegalArgumentException("包含未声明的训练参数");
        params.set("ultralytics_parameters", values);
    }

    public synchronized JSONObject inspectConfiguration(String kind, String id) {
        Path component = components.localPath(kind, id);
        try {
            List<String> models = modelChoices(component);
            List<String> weights = weightChoices(component);
            if (models.isEmpty()) throw new IllegalArgumentException("组件仓库中没有可用的模型 YAML");
            Path savedPath = configurationPath(kind, id);
            JSONObject saved = new JSONObject();
            String commit = revision(component);
            if (Files.isRegularFile(savedPath, LinkOption.NOFOLLOW_LINKS)) {
                JSONObject candidate = JSONUtil.readJSONObject(savedPath.toFile(), StandardCharsets.UTF_8);
                if (commit.equals(candidate.getStr("component_commit"))) saved = candidate;
            }
            String model = saved.getStr("model");
            if (!models.contains(model)) model = models.size() == 1 ? models.get(0) : defaultModel(models);
            String weight = saved.getStr("pretrained", "");
            if (!weight.isBlank() && !weights.contains(weight) && !validExternalWeight(weight)) weight = "";
            return JSONUtil.createObj().set("models", models).set("weights", weights).set("model", model)
                    .set("pretrained", weight).set("componentCommit", commit).set("externalWeightAllowed", true);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取组件训练配置：" + e.getMessage(), e);
        }
    }

    public synchronized Map<String, Object> saveConfiguration(String kind, String id, String model, String weight) {
        Path component = components.localPath(kind, id);
        try {
            List<String> models = modelChoices(component);
            List<String> weights = weightChoices(component);
            if (!models.contains(model)) throw new IllegalArgumentException("请选择组件仓库内有效的模型 YAML");
            String selectedWeight = weight == null ? "" : weight.trim();
            if (!selectedWeight.isBlank() && !weights.contains(selectedWeight) && !validExternalWeight(selectedWeight))
                throw new IllegalArgumentException("请选择组件内权重或有效的本机 .pt 文件路径");
            Path destination = configurationPath(kind, id);
            Files.createDirectories(destination.getParent());
            JSONObject saved = JSONUtil.createObj().set("model", model).set("pretrained", selectedWeight)
                    .set("component_commit", revision(component));
            Path temporary = Files.createTempFile(destination.getParent(), ".training-config-", ".tmp");
            try {
                Files.writeString(temporary, saved.toStringPretty(), StandardCharsets.UTF_8);
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return Map.of("id", id, "kind", kind, "model", model, "pretrained", selectedWeight);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法保存组件训练配置，请检查本机目录写权限");
        }
    }

    private Path configurationPath(String kind, String id) {
        if (!Set.of("baseline", "algorithm").contains(kind) || id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("组件 ID 或类型无效");
        String[] parts = id.split("/", 2);
        Path base = root.resolve("local-components/training-configs").toAbsolutePath().normalize();
        Path file = base.resolve(kind).resolve(parts[0]).resolve(parts[1] + ".json").normalize();
        if (!file.startsWith(base)) throw new IllegalArgumentException("组件配置路径无效");
        Path current = base;
        for (Path segment : base.relativize(file.getParent())) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("组件配置目录不能是符号链接");
        }
        try {
            if (Files.exists(file.getParent()) && !file.getParent().toRealPath().startsWith(base.toRealPath()))
                throw new IllegalArgumentException("组件配置目录超出本机配置根目录");
        } catch (IOException e) { throw new IllegalArgumentException("组件配置目录无效"); }
        return file;
    }

    private List<String> modelChoices(Path component) throws IOException {
        Path base = component.toRealPath();
        List<String> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(base)) {
            for (Path path : paths.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yaml")
                            || file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted().toList()) {
                if (!path.toRealPath().startsWith(base)) continue;
                String relative = base.relativize(path).toString().replace(FileSystems.getDefault().getSeparator(), "/");
                if ((relative.startsWith("ultralytics/cfg/models/") || !relative.contains("/")) && isModelYaml(path)) result.add(relative);
            }
        }
        return result;
    }

    private List<String> weightChoices(Path component) throws IOException {
        Path base = component.toRealPath();
        List<String> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(base, 5)) {
            for (Path path : paths.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pt"))
                    .sorted().toList()) {
                String relative = base.relativize(path).toString().replace(FileSystems.getDefault().getSeparator(), "/");
                if (path.toRealPath().startsWith(base) && (!relative.contains("/") || relative.startsWith("weights/"))) result.add(relative);
            }
        }
        return result;
    }

    private boolean isModelYaml(Path path) {
        try {
            if (Files.size(path) > 512 * 1024) return false;
            String yaml = Files.readString(path, StandardCharsets.UTF_8);
            List<String> lines = yaml.lines().map(String::strip).toList();
            return lines.stream().anyMatch(line -> line.matches("backbone\\s*:.*"))
                    && lines.stream().anyMatch(line -> line.matches("head\\s*:.*"));
        } catch (Exception ignored) { return false; }
    }

    private String defaultModel(List<String> models) {
        return models.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains("yolov8n.yaml"))
                .findFirst().orElse(models.get(0));
    }

    private boolean validExternalWeight(String value) {
        try {
            Path supplied = Path.of(value);
            if (!supplied.isAbsolute()) return false;
            Path path = supplied.normalize();
            return path.toString().toLowerCase(Locale.ROOT).endsWith(".pt") && Files.isRegularFile(path);
        } catch (InvalidPathException e) { return false; }
    }

    private byte[] savedConfigurationBytes(String kind, String id) {
        Path file = configurationPath(kind, id);
        try { return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(file) : new byte[0]; }
        catch (IOException e) { return new byte[0]; }
    }

    private JSONObject savedConfiguration(String kind, String id, String commit) {
        Path file = configurationPath(kind, id);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
            JSONObject value = JSONUtil.readJSONObject(file.toFile(), StandardCharsets.UTF_8);
            return commit.equals(value.getStr("component_commit")) ? value : null;
        } catch (Exception e) { return null; }
    }
    private String revision(Path path) throws Exception {
        Process p = new ProcessBuilder("git", "-c", "safe.directory=" + path.toString().replace("\\", "/"), "rev-parse", "HEAD").directory(path.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); return ""; }
        return p.exitValue() == 0 ? new String(p.getInputStream().readAllBytes()).trim() : "";
    }
}
