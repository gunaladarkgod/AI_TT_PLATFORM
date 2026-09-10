package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

/**
 * 扫描项目内 research 目录中的算法清单。该服务只读取固定目录的 YAML，
 * 不执行算法代码、不下载论文源码，也不依赖算法模板数据库表。
 */
@Slf4j
@Service
public class ResearchCatalogService {

    private static final String MANIFEST_FILE = "algorithm.yaml";
    private static final String DIRECTION_FILE = "direction.yaml";
    private static final String IMPROVEMENTS_DIR = "improvements";
    private static final long MAX_MANIFEST_SIZE = 512 * 1024L;
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> ENGINES = Set.of("mmdet", "ultralytics", "paper");
    private static final Set<String> STATUSES = Set.of("draft", "review", "published", "deprecated", "archived");
    private static final Set<String> DIRECTION_STATUSES = Set.of("active", "deprecated", "archived");
    private static final Set<String> DATA_FORMATS = Set.of("coco", "yolo", "custom");

    public List<Map<String, Object>> listDirections() {
        return scan().directions;
    }

    public List<Map<String, Object>> listBaselines(String directionId) {
        if (!validSegment(directionId)) return List.of();
        Catalog catalog = scan();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> baseline : catalog.baselines) {
            if (directionId.equals(baseline.get("direction"))) out.add(baseline);
        }
        return out;
    }

    public List<Map<String, Object>> listImprovements(String baselineId) {
        if (!validPackageId(baselineId, 2)) return List.of();
        Catalog catalog = scan();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> improvement : catalog.improvements) {
            if (baselineId.equals(improvement.get("parent"))) out.add(improvement);
        }
        return out;
    }

    public Map<String, Object> detail(String packageId) {
        if (!validPackageId(packageId, 2) && !validPackageId(packageId, 3)) return null;
        Catalog catalog = scan();
        for (Map<String, Object> item : catalog.allPackages()) {
            if (packageId.equals(item.get("id"))) return item;
        }
        return null;
    }

    /**
     * Resolve a baseline and a set of improvement packages into one deterministic stack.
     * This is deliberately metadata-only: it validates package relations and exposes the
     * declared parameters, but never imports or executes research code.
     */
    public Map<String, Object> resolveStack(String baselineId, Collection<String> requestedImprovementIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        Catalog catalog = scan();
        Map<String, Object> baseline = null;
        Map<String, Map<String, Object>> packages = new LinkedHashMap<>();
        for (Map<String, Object> item : catalog.allPackages()) {
            String id = stringValue(item.get("id"));
            packages.put(id, item);
            if (baselineId != null && baselineId.equals(id) && "baseline".equals(item.get("kind"))) {
                baseline = item;
            }
        }
        if (baseline == null) {
            errors.add("基线不存在：" + baselineId);
            return stackResult(result, null, List.of(), List.of(), errors);
        }
        if (!Boolean.TRUE.equals(baseline.get("valid"))) {
            errors.add("基线清单无效：" + String.join("；", stringList(baseline.get("validation_errors"))));
            return stackResult(result, baseline, List.of(), List.of(), errors);
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        if (requestedImprovementIds != null) {
            for (String raw : requestedImprovementIds) {
                String id = stringValue(raw);
                if (id.isBlank()) continue;
                if (!requested.add(id)) errors.add("改进包重复选择：" + id);
            }
        }
        List<Map<String, Object>> improvements = new ArrayList<>();
        for (String id : requested) {
            Map<String, Object> improvement = packages.get(id);
            if (improvement == null || !"improvement".equals(improvement.get("kind"))) {
                errors.add("改进包不存在：" + id);
                continue;
            }
            if (!baselineId.equals(stringValue(improvement.get("parent")))) {
                errors.add("改进包不属于当前基线：" + id);
                continue;
            }
            if (!Boolean.TRUE.equals(improvement.get("valid"))) {
                errors.add("改进包清单无效：" + id + "（" + String.join("；", stringList(improvement.get("validation_errors"))) + "）");
                continue;
            }
            Map<String, Object> stack = mapValue(improvement.get("stack"));
            if (!Boolean.TRUE.equals(stack.get("enabled"))) {
                errors.add("改进包未启用叠加：" + id);
                continue;
            }
            improvements.add(improvement);
        }

        improvements.sort(Comparator
                .comparingInt((Map<String, Object> item) -> numberValue(mapValue(item.get("stack")).get("priority"), 0))
                .thenComparing(item -> stringValue(item.get("id"))));

        Set<String> selectedIds = new LinkedHashSet<>();
        for (Map<String, Object> item : improvements) selectedIds.add(stringValue(item.get("id")));
        for (Map<String, Object> item : improvements) {
            String id = stringValue(item.get("id"));
            Map<String, Object> stack = mapValue(item.get("stack"));
            for (String dependency : stringList(stack.get("requires"))) {
                if (!selectedIds.contains(dependency)) errors.add("改进包 " + id + " 需要同时选择：" + dependency);
            }
            for (String conflict : stringList(stack.get("conflicts"))) {
                if (selectedIds.contains(conflict)) errors.add("改进包冲突：" + id + " 与 " + conflict);
            }
        }

        Map<String, String> overrideOwners = new LinkedHashMap<>();
        for (Map<String, Object> item : improvements) {
            String id = stringValue(item.get("id"));
            for (String path : stringList(mapValue(item.get("stack")).get("override_paths"))) {
                String previous = overrideOwners.putIfAbsent(path, id);
                if (previous != null && !previous.equals(id)) {
                    errors.add("配置覆盖路径冲突：" + path + "（" + previous + " 与 " + id + "）");
                }
            }
        }

        List<Map<String, Object>> parameters = new ArrayList<>();
        Set<String> parameterKeys = new LinkedHashSet<>();
        appendParameters(baseline, parameters, parameterKeys, errors);
        for (Map<String, Object> item : improvements) appendParameters(item, parameters, parameterKeys, errors);
        return stackResult(result, baseline, improvements, parameters, errors);
    }

    private Catalog scan() {
        Path root = WorkspacePathUtil.workspaceRoot().resolve("research").toAbsolutePath().normalize();
        Catalog catalog = new Catalog();
        if (!Files.isDirectory(root)) return catalog;

        for (Path directionDir : childDirectories(root)) {
            Map<String, Object> direction = loadDirection(root, directionDir);
            if (direction == null) continue;
            catalog.directions.add(direction);
            String directionId = String.valueOf(direction.get("id"));
            for (Path baselineDir : childDirectories(directionDir)) {
                if (IMPROVEMENTS_DIR.equals(baselineDir.getFileName().toString())) continue;
                Map<String, Object> baseline = loadPackage(root, baselineDir, "baseline", directionId, null);
                if (baseline == null) continue;
                catalog.baselines.add(baseline);
                String baselineId = String.valueOf(baseline.get("id"));
                Path improvementsDir = baselineDir.resolve(IMPROVEMENTS_DIR);
                if (!Files.isDirectory(improvementsDir)) continue;
                for (Path improvementDir : childDirectories(improvementsDir)) {
                    Map<String, Object> improvement = loadPackage(root, improvementDir, "improvement", directionId, baselineId);
                    if (improvement != null) {
                        validateImprovementCompatibility(baseline, improvement);
                        catalog.improvements.add(improvement);
                    }
                }
            }
        }
        return catalog;
    }

    private Map<String, Object> loadDirection(Path root, Path directionDir) {
        Path manifestPath = directionDir.resolve(DIRECTION_FILE);
        Map<String, Object> manifest = readYaml(manifestPath);
        if (manifest == null) return null;
        List<String> errors = new ArrayList<>();
        String dirName = directionDir.getFileName().toString();
        String id = stringValue(manifest.get("id"));
        if (!dirName.equals(id) || !validSegment(id)) errors.add("方向 id 必须与目录名一致且仅包含小写字母、数字和连字符");
        String status = stringValue(manifest.get("status"));
        if (!DIRECTION_STATUSES.contains(status)) {
            errors.add("方向状态仅允许 active、deprecated 或 archived");
        }
        if (stringValue(manifest.get("name")).isBlank()) errors.add("缺少方向名称 name");
        if (!errors.isEmpty()) {
            log.warn("Ignoring invalid research direction {}: {}", manifestPath, errors);
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>(manifest);
        out.put("location", normalizedRelative(root, directionDir));
        return out;
    }

    private Map<String, Object> loadPackage(Path root, Path packageDir, String expectedKind,
                                            String directionId, String expectedParent) {
        Path manifestPath = packageDir.resolve(MANIFEST_FILE);
        Map<String, Object> manifest = readYaml(manifestPath);
        if (manifest == null) return null;
        List<String> errors = validatePackage(manifest, packageDir, expectedKind, directionId, expectedParent);
        Map<String, Object> out = new LinkedHashMap<>(manifest);
        out.put("location", normalizedRelative(root, packageDir));
        out.put("valid", errors.isEmpty());
        out.put("validation_errors", errors);
        if (!errors.isEmpty()) log.warn("Research package validation failed at {}: {}", manifestPath, errors);
        return out;
    }

    private List<String> validatePackage(Map<String, Object> manifest, Path packageDir, String expectedKind,
                                         String directionId, String expectedParent) {
        List<String> errors = new ArrayList<>();
        String id = stringValue(manifest.get("id"));
        int segments = "baseline".equals(expectedKind) ? 2 : 3;
        if (!validPackageId(id, segments)) errors.add("id 格式不正确");
        if (!expectedKind.equals(stringValue(manifest.get("kind")))) errors.add("kind 必须为 " + expectedKind);
        if (!directionId.equals(stringValue(manifest.get("direction")))) errors.add("direction 必须与所属方向一致");
        if (!ENGINES.contains(stringValue(manifest.get("engine")))) errors.add("engine 必须为 mmdet、ultralytics 或 paper");
        if (stringValue(manifest.get("engine_version")).isBlank()) errors.add("缺少 engine_version");
        if (!STATUSES.contains(stringValue(manifest.get("status")))) errors.add("status 不受支持");
        if (!DATA_FORMATS.contains(stringValue(manifest.get("data_format")))) errors.add("data_format 不受支持");
        if (stringValue(manifest.get("name")).isBlank()) errors.add("缺少 name");
        if (!safeRelativePath(stringValue(manifest.get("entry")))) errors.add("entry 必须是算法包内的相对路径");
        String sourceRoot = stringValue(manifest.get("source_root"));
        if (!sourceRoot.isBlank() && !safeRelativePath(sourceRoot)) errors.add("source_root 必须是算法包内的相对路径");
        if (expectedParent != null && !expectedParent.equals(stringValue(manifest.get("parent")))) {
            errors.add("parent 必须与所在基线一致");
        }
        if (id.startsWith(directionId + "/")) {
            String packageName = packageDir.getFileName().toString();
            String expectedIdTail = "baseline".equals(expectedKind) ? packageName : packageDir.getParent().getParent().getFileName() + "/" + packageName;
            if (!id.equals(directionId + "/" + expectedIdTail)) errors.add("id 必须与算法包目录层级一致");
        }
        validateParameters(manifest.get("parameters"), errors);
        if ("improvement".equals(expectedKind)) validateStack(manifest.get("stack"), errors);
        return errors;
    }

    private void validateImprovementCompatibility(Map<String, Object> baseline, Map<String, Object> improvement) {
        List<String> errors = new ArrayList<>(stringList(improvement.get("validation_errors")));
        for (String field : List.of("engine", "engine_version", "data_format")) {
            if (!stringValue(baseline.get(field)).equals(stringValue(improvement.get(field)))) {
                errors.add(field + " 必须与父基线一致");
            }
        }
        if (!errors.isEmpty()) {
            improvement.put("valid", false);
            improvement.put("validation_errors", errors);
        }
    }

    private void validateStack(Object raw, List<String> errors) {
        if (!(raw instanceof Map<?, ?>)) {
            errors.add("改进包缺少 stack 对象");
            return;
        }
        Map<String, Object> stack = mapValue(raw);
        if (!(stack.get("enabled") instanceof Boolean)) errors.add("stack.enabled 必须是布尔值");
        if (!(stack.get("priority") instanceof Number)) errors.add("stack.priority 必须是数字");
        for (String field : List.of("requires", "conflicts", "override_paths")) {
            Object value = stack.get(field);
            if (!(value instanceof Collection<?>)) {
                errors.add("stack." + field + " 必须是列表");
                continue;
            }
            for (String item : stringList(value)) {
                if ("override_paths".equals(field)) {
                    if (item.isBlank() || item.contains("..")) errors.add("stack.override_paths 包含无效路径");
                } else if (!validPackageId(item, 3)) {
                    errors.add("stack." + field + " 必须使用完整改进包 ID：" + item);
                }
            }
        }
    }

    private Map<String, Object> stackResult(Map<String, Object> out, Map<String, Object> baseline,
                                             List<Map<String, Object>> improvements, List<Map<String, Object>> parameters,
                                             List<String> errors) {
        out.put("valid", errors.isEmpty());
        out.put("errors", errors);
        out.put("baseline", baseline);
        out.put("improvements", improvements);
        out.put("parameters", parameters);
        return out;
    }

    private void appendParameters(Map<String, Object> item, List<Map<String, Object>> out,
                                  Set<String> keys, List<String> errors) {
        Object raw = item.get("parameters");
        if (!(raw instanceof Collection<?> values)) return;
        for (Object value : values) {
            Map<String, Object> parameter = mapValue(value);
            String key = stringValue(parameter.get("key"));
            if (!keys.add(key)) {
                errors.add("参数 key 冲突：" + key + "（" + stringValue(item.get("id")) + "）");
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(parameter);
            copy.put("package_id", item.get("id"));
            copy.put("package_name", item.get("name"));
            out.add(copy);
        }
    }

    private void validateParameters(Object raw, List<String> errors) {
        if (raw == null) return;
        if (!(raw instanceof Collection<?> parameters)) {
            errors.add("parameters 必须是列表");
            return;
        }
        Set<String> keys = new LinkedHashSet<>();
        Set<String> allowedTypes = Set.of("number", "integer", "boolean", "select", "string", "json");
        for (Object parameter : parameters) {
            if (!(parameter instanceof Map<?, ?> map)) {
                errors.add("parameters 中存在非对象项");
                continue;
            }
            String key = stringValue(map.get("key"));
            String type = stringValue(map.get("type"));
            if (key.isBlank() || !keys.add(key)) errors.add("参数 key 缺失或重复：" + key);
            if (!allowedTypes.contains(type)) errors.add("参数 type 不受支持：" + key);
            if (stringValue(map.get("label")).isBlank()) errors.add("参数缺少 label：" + key);
            if (stringValue(map.get("description")).isBlank()) errors.add("参数缺少 description：" + key);
            if (stringValue(map.get("config_path")).isBlank()) errors.add("参数缺少 config_path：" + key);
        }
    }

    private Map<String, Object> readYaml(Path path) {
        if (!Files.isRegularFile(path)) {
            log.warn("Research manifest does not exist: {}", path);
            return null;
        }
        try {
            if (Files.size(path) > MAX_MANIFEST_SIZE) {
                log.warn("Research manifest exceeds size limit: {}", path);
                return null;
            }
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Yaml yaml = new Yaml(new SafeConstructor(options));
            try (InputStream input = Files.newInputStream(path)) {
                Object raw = yaml.load(input);
                if (!(raw instanceof Map<?, ?> map)) {
                    log.warn("Research manifest is not a YAML object: {}", path);
                    return null;
                }
                return normalizeMap(map);
            }
        } catch (Exception e) {
            log.warn("Failed to read research manifest {}: {}", path, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> normalizeMap(Map<?, ?> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return out;
    }

    private Map<String, Object> mapValue(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        return normalizeMap(map);
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof Collection<?> values)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object value : values) out.add(stringValue(value));
        return out;
    }

    private int numberValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) return normalizeMap(map);
        if (value instanceof Collection<?> values) {
            List<Object> out = new ArrayList<>();
            for (Object item : values) out.add(normalizeValue(item));
            return out;
        }
        return value;
    }

    private List<Path> childDirectories(Path parent) {
        try (Stream<Path> stream = Files.list(parent)) {
            return stream.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to scan research directory {}: {}", parent, e.getMessage());
            return List.of();
        }
    }

    private boolean validSegment(String value) {
        return value != null && SEGMENT.matcher(value).matches();
    }

    private boolean validPackageId(String id, int segments) {
        if (id == null) return false;
        String[] values = id.split("/");
        if (values.length != segments) return false;
        for (String value : values) if (!validSegment(value)) return false;
        return true;
    }

    private boolean safeRelativePath(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            Path path = Paths.get(raw).normalize();
            return !path.isAbsolute() && !path.startsWith("..") && !raw.contains(":");
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizedRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class Catalog {
        private final List<Map<String, Object>> directions = new ArrayList<>();
        private final List<Map<String, Object>> baselines = new ArrayList<>();
        private final List<Map<String, Object>> improvements = new ArrayList<>();

        private List<Map<String, Object>> allPackages() {
            List<Map<String, Object>> out = new ArrayList<>(baselines);
            out.addAll(improvements);
            return out;
        }
    }
}
