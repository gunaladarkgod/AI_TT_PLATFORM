package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads and validates the repository-owned research registry. No database or Git operation is used. */
@Slf4j
@Service
public class ResearchRegistryService {
    private static final long MAX_YAML_SIZE = 128 * 1024L;
    private static final long MAX_DOCUMENT_SIZE = 512 * 1024L;
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> ENGINES = Set.of("mmdet", "ultralytics", "custom", "paper");
    private static final Set<String> DATA_FORMATS = Set.of("coco", "yolo", "custom");
    private static final Set<String> STATUSES = Set.of("draft", "review", "published");
    private final Path workspaceRoot;

    public ResearchRegistryService() {
        this(null);
    }

    ResearchRegistryService(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot == null ? WorkspacePathUtil.workspaceRoot() : workspaceRoot.toAbsolutePath().normalize();
    }

    public Map<String, Object> load() {
        Path root = workspaceRoot.resolve("research-registry").toAbsolutePath().normalize();
        List<String> errors = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", false);
        result.put("root", "research-registry");
        result.put("registry", Map.of());
        result.put("directions", List.of());
        result.put("algorithms", List.of());
        result.put("conventions", List.of());
        result.put("errors", errors);
        if (!Files.isDirectory(root)) {
            errors.add("登记目录不存在：research-registry");
            return result;
        }

        Map<String, Object> registry = readYaml(root.resolve("registry.yaml"), errors, "registry.yaml");
        validateRegistry(registry, errors);
        result.put("registry", registry);
        result.put("conventions", readConventions(root, registry, errors));

        List<Map<String, Object>> directions = new ArrayList<>();
        Set<String> directionIds = new LinkedHashSet<>();
        for (Path file : childFiles(root.resolve("directions"), ".yaml")) {
            Map<String, Object> direction = readYaml(file, errors, relative(root, file));
            validateDirection(direction, file, root, directionIds, errors);
            if (!direction.isEmpty()) {
                direction.put("location", relative(root, file));
                directions.add(direction);
            }
        }
        result.put("directions", directions);

        List<Map<String, Object>> algorithms = new ArrayList<>();
        Set<String> algorithmIds = new LinkedHashSet<>();
        for (Path file : childFiles(root.resolve("algorithms"), ".yaml")) {
            Map<String, Object> algorithm = readYaml(file, errors, relative(root, file));
            validateAlgorithm(algorithm, file, root, directionIds, algorithmIds, errors);
            if (!algorithm.isEmpty()) {
                algorithm.put("location", relative(root, file));
                algorithms.add(algorithm);
            }
        }
        algorithms.sort(Comparator.comparing(item -> string(item.get("id"))));
        result.put("algorithms", algorithms);
        result.put("valid", errors.isEmpty());
        return result;
    }

    private void validateRegistry(Map<String, Object> registry, List<String> errors) {
        if (registry.isEmpty()) return;
        if (number(registry.get("schema_version")) != 1) errors.add("registry.yaml 的 schema_version 必须为 1");
        if (string(registry.get("name")).isBlank()) errors.add("registry.yaml 缺少 name");
        Object conventions = registry.get("conventions");
        if (!(conventions instanceof Collection<?>)) errors.add("registry.yaml 的 conventions 必须是列表");
    }

    private List<Map<String, Object>> readConventions(Path root, Map<String, Object> registry, List<String> errors) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = registry.get("conventions");
        if (!(raw instanceof Collection<?> values)) return result;
        Set<String> ids = new LinkedHashSet<>();
        for (Object value : values) {
            Map<String, Object> convention = map(value);
            String id = string(convention.get("id"));
            String pathValue = string(convention.get("path"));
            if (!validSegment(id) || !ids.add(id)) errors.add("公约 id 缺失或重复：" + id);
            if (!safeRelative(pathValue) || !pathValue.endsWith(".md")) errors.add("公约路径无效：" + pathValue);
            Path path = root.resolve(pathValue).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                errors.add("公约文件不存在：" + pathValue);
                continue;
            }
            try {
                if (Files.size(path) > MAX_DOCUMENT_SIZE) {
                    errors.add("公约文件过大：" + pathValue);
                    continue;
                }
                Map<String, Object> output = new LinkedHashMap<>(convention);
                output.put("path", relative(root, path));
                output.put("content", Files.readString(path, StandardCharsets.UTF_8));
                result.add(output);
            } catch (Exception e) {
                errors.add("读取公约失败：" + pathValue);
            }
        }
        return result;
    }

    private void validateDirection(Map<String, Object> direction, Path file, Path root, Set<String> ids,
                                   List<String> errors) {
        if (direction.isEmpty()) return;
        String id = string(direction.get("id"));
        String expected = stripExtension(file.getFileName().toString());
        if (number(direction.get("schema_version")) != 1) errors.add("方向 schema_version 必须为 1：" + id);
        if (!expected.equals(id) || !validSegment(id)) errors.add("方向 id 必须与文件名一致且使用小写字母、数字和连字符：" + relative(root, file));
        if (!ids.add(id)) errors.add("方向 id 重复：" + id);
        if (string(direction.get("name")).isBlank()) errors.add("方向缺少 name：" + id);
        if (!Set.of("active", "deprecated", "archived").contains(string(direction.get("status")))) errors.add("方向状态无效：" + id);
        validateStringList(direction.get("data_formats"), DATA_FORMATS, "data_formats", id, errors);
        validateStringList(direction.get("metrics"), Set.of(), "metrics", id, errors);
        if (!(direction.get("engine_compatibility") instanceof Collection<?>)) errors.add("方向缺少 engine_compatibility 列表：" + id);
    }

    private void validateAlgorithm(Map<String, Object> algorithm, Path file, Path root, Set<String> directions,
                                   Set<String> ids, List<String> errors) {
        if (algorithm.isEmpty()) return;
        String id = string(algorithm.get("id"));
        if (!validId(id)) errors.add("算法 id 格式无效：" + id);
        if (!ids.add(id)) errors.add("算法 id 重复：" + id);
        String direction = string(algorithm.get("direction"));
        if (!directions.contains(direction)) errors.add("算法引用了未登记方向：" + direction + "（" + id + "）");
        if (number(algorithm.get("schema_version")) != 1) errors.add("算法 schema_version 必须为 1：" + id);
        if (!Set.of("baseline", "improvement").contains(string(algorithm.get("kind")))) errors.add("算法 kind 必须为 baseline 或 improvement：" + id);
        for (String field : List.of("name", "description", "data_format", "algorithm_manifest")) {
            if (string(algorithm.get(field)).isBlank()) errors.add("算法缺少 " + field + "：" + id);
        }
        if (!STATUSES.contains(string(algorithm.get("status")))) errors.add("算法状态无效：" + id);
        if (!DATA_FORMATS.contains(string(algorithm.get("data_format")))) errors.add("算法 data_format 无效：" + id);
        Map<String, Object> engine = map(algorithm.get("engine"));
        if (!ENGINES.contains(string(engine.get("id")))) errors.add("算法 engine.id 无效：" + id);
        if (string(engine.get("version")).isBlank()) errors.add("算法 engine.version 缺失：" + id);
        Map<String, Object> repository = map(algorithm.get("repository"));
        String url = string(repository.get("url"));
        if (!url.startsWith("https://") || url.contains("@") || url.contains(" ")) errors.add("算法 repository.url 必须是安全的 HTTPS 地址：" + id);
        if (string(repository.get("default_ref")).isBlank()) errors.add("算法 repository.default_ref 缺失：" + id);
        if (!algorithm.containsKey("platform")) errors.add("算法缺少 platform：" + id);
        if (!safeRelative(string(algorithm.get("algorithm_manifest")))) errors.add("算法 algorithm_manifest 必须是相对路径：" + id);
    }

    private void validateStringList(Object raw, Set<String> allowed, String field, String owner, List<String> errors) {
        if (!(raw instanceof Collection<?> values)) {
            errors.add(field + " 必须是列表：" + owner);
            return;
        }
        for (Object value : values) {
            String item = string(value);
            if (item.isBlank() || (!allowed.isEmpty() && !allowed.contains(item))) errors.add(field + " 包含无效值：" + item + "（" + owner + "）");
        }
    }

    private Map<String, Object> readYaml(Path path, List<String> errors, String displayPath) {
        if (!Files.isRegularFile(path)) {
            errors.add("登记文件不存在或不可读：" + displayPath);
            return new LinkedHashMap<>();
        }
        try {
            if (Files.size(path) > MAX_YAML_SIZE) {
                errors.add("登记文件过大：" + displayPath);
                return new LinkedHashMap<>();
            }
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object raw;
            try (InputStream input = Files.newInputStream(path)) {
                raw = new Yaml(new SafeConstructor(options)).load(input);
            }
            if (!(raw instanceof Map<?, ?> map)) {
                errors.add("登记文件必须是 YAML 对象：" + displayPath);
                return new LinkedHashMap<>();
            }
            return normalize(map);
        } catch (Exception e) {
            log.warn("Failed to read research registry file {}", path, e);
            errors.add("读取登记文件失败：" + displayPath + "（" + e.getMessage() + "）");
            return new LinkedHashMap<>();
        }
    }

    private List<Path> childFiles(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> normalize(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), normalizeValue(value)));
        return result;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) return normalize(map);
        if (value instanceof Collection<?> values) return values.stream().map(this::normalizeValue).toList();
        return value;
    }

    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> raw ? normalize(raw) : Map.of(); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private int number(Object value) { return value instanceof Number n ? n.intValue() : -1; }
    private boolean validSegment(String value) { return value != null && SEGMENT.matcher(value).matches(); }
    private boolean validId(String value) {
        String[] parts = value == null ? new String[0] : value.split("/");
        return parts.length == 2 && validSegment(parts[0]) && validSegment(parts[1]);
    }
    private boolean safeRelative(String value) {
        if (value.isBlank() || value.contains(":") || value.contains("..")) return false;
        try { Path path = Path.of(value).normalize(); return !path.isAbsolute() && !path.startsWith(".."); }
        catch (Exception e) { return false; }
    }
    private String relative(Path root, Path path) { return root.relativize(path).toString().replace('\\', '/'); }
    private String stripExtension(String value) { return value.endsWith(".yaml") ? value.substring(0, value.length() - 5) : value; }
}
