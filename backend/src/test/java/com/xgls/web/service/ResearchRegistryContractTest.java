package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry is intentionally file-based in this phase. This test keeps the
 * frozen v1 contract and the legacy in-tree migration records in sync before a
 * runtime Registry Service is introduced.
 */
class ResearchRegistryContractTest {

    @Test
    void registryCoversEveryLegacyManifestAndUsesOnlyDeclaredValues() throws Exception {
        Path workspace = WorkspacePathUtil.workspaceRoot();
        Path root = workspace.resolve("research-registry");
        Map<String, Object> registry = readYaml(root.resolve("registry.yaml"));
        assertEquals(1, number(registry.get("schema_version")));

        Map<String, Object> allowed = map(registry.get("allowed"));
        Set<String> kinds = strings(allowed.get("algorithm_kinds"));
        Set<String> statuses = strings(allowed.get("statuses"));
        Set<String> engines = strings(allowed.get("engines"));
        Set<String> formats = strings(allowed.get("data_formats"));
        Set<String> sourceTypes = strings(allowed.get("source_types"));

        Map<String, Map<String, Object>> directions = new LinkedHashMap<>();
        for (Path path : yamlFiles(root.resolve("directions"))) {
            Map<String, Object> direction = readYaml(path);
            String id = text(direction.get("id"));
            assertFalse(id.isBlank(), "方向缺少 id: " + path);
            assertTrue(directions.putIfAbsent(id, direction) == null, "方向 ID 重复: " + id);
            assertEquals(1, number(direction.get("schema_version")), "方向协议版本错误: " + id);
            assertFalse(text(direction.get("name")).isBlank(), "方向缺少名称: " + id);
        }

        Map<String, Map<String, Object>> records = new LinkedHashMap<>();
        for (Path path : yamlFiles(root.resolve("algorithms"))) {
            Map<String, Object> record = readYaml(path);
            String id = text(record.get("id"));
            assertFalse(id.isBlank(), "算法登记缺少 id: " + path);
            assertTrue(records.putIfAbsent(id, record) == null, "算法登记 ID 重复: " + id);
            assertEquals(1, number(record.get("schema_version")), "算法协议版本错误: " + id);
            assertTrue(kinds.contains(text(record.get("kind"))), "算法 kind 不受支持: " + id);
            assertTrue(statuses.contains(text(record.get("status"))), "算法 status 不受支持: " + id);
            assertTrue(directions.containsKey(text(record.get("direction"))), "算法引用未知方向: " + id);
            assertFalse(text(record.get("name")).isBlank(), "算法缺少名称: " + id);
            assertFalse(text(record.get("description")).isBlank(), "算法缺少说明: " + id);

            Map<String, Object> engine = map(record.get("engine"));
            assertTrue(engines.contains(text(engine.get("id"))), "算法引擎不受支持: " + id);
            assertFalse(text(engine.get("version")).isBlank(), "算法缺少引擎版本: " + id);
            assertTrue(formats.contains(text(record.get("data_format"))), "算法数据格式不受支持: " + id);

            Map<String, Object> source = map(record.get("source"));
            String sourceType = text(source.get("type"));
            assertTrue(sourceTypes.contains(sourceType), "算法来源类型不受支持: " + id);
            if ("in-tree".equals(sourceType)) {
                String sourcePath = text(source.get("path"));
                String manifest = text(source.get("manifest"));
                assertSafeRelative(sourcePath, "算法来源路径不安全: " + id);
                assertSafeRelative(manifest, "算法清单路径不安全: " + id);
                assertTrue(Files.isRegularFile(workspace.resolve(sourcePath).resolve(manifest)),
                        "项目内算法清单不存在: " + id);
            }
            if ("external-git".equals(sourceType)) {
                assertTrue(text(source.get("repository")).startsWith("https://") || text(source.get("repository")).startsWith("git@"),
                        "外部算法仓库地址无效: " + id);
                assertFalse(text(source.get("default_ref")).isBlank(), "外部算法缺少 default_ref: " + id);
                assertSafeRelative(text(source.get("manifest")), "外部算法清单路径不安全: " + id);
            }
        }

        for (Map<String, Object> record : records.values()) {
            if ("improvement".equals(text(record.get("kind")))) {
                String parent = text(record.get("parent"));
                assertNotNull(records.get(parent), "改进包父基线未登记: " + record.get("id"));
                assertEquals("baseline", text(records.get(parent).get("kind")), "改进包父项必须是基线: " + record.get("id"));
            }
            if ("standalone-algorithm".equals(text(record.get("kind")))) {
                Map<String, Object> derived = map(record.get("derived_from"));
                Map<String, Object> baseline = map(derived.get("baseline"));
                assertFalse(text(baseline.get("repository")).isBlank(), "独立算法缺少基线仓库: " + record.get("id"));
                assertFalse(text(baseline.get("ref")).isBlank(), "独立算法缺少基线版本: " + record.get("id"));
                assertFalse(text(baseline.get("commit")).isBlank(), "独立算法缺少基线提交: " + record.get("id"));
            }
        }

        Set<String> legacyIds = legacyManifestIds(workspace.resolve("research"));
        Set<String> inTreeIds = new LinkedHashSet<>();
        for (Map<String, Object> record : records.values()) {
            if ("in-tree".equals(text(map(record.get("source")).get("type")))) inTreeIds.add(text(record.get("id")));
        }
        assertEquals(legacyIds, inTreeIds, "旧 research 清单必须与项目内来源登记一一对应");
        assertEquals(11, inTreeIds.size(), "当前应登记 8 个基线与 3 个改进包");
    }

    @Test
    void externalAlgorithmTemplateIsAValidProtocolSkeleton() throws Exception {
        Path template = WorkspacePathUtil.workspaceRoot().resolve("research-registry/templates/external-algorithm.yaml");
        Map<String, Object> record = readYaml(template);
        assertEquals(1, number(record.get("schema_version")));
        assertEquals("external-git", text(map(record.get("source")).get("type")));
        assertEquals("standalone-algorithm", text(record.get("kind")));
        assertEquals("algorithm.yaml", text(map(record.get("source")).get("manifest")));
        assertEquals("derived-from.yaml", text(map(record.get("source")).get("derived_manifest")));
        assertEquals("draft", text(record.get("status")));
    }

    private Set<String> legacyManifestIds(Path root) throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(path -> path.getFileName().toString().equals("algorithm.yaml")).toList()) {
                ids.add(text(readYaml(path).get("id")));
            }
        }
        return ids;
    }

    private List<Path> yamlFiles(Path directory) throws Exception {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".yaml")).sorted().toList();
        }
    }

    private Map<String, Object> readYaml(Path path) throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream input = Files.newInputStream(path)) {
            Object raw = new Yaml(new SafeConstructor(options)).load(input);
            assertTrue(raw instanceof Map<?, ?>, "YAML 根节点必须是对象: " + path);
            return map(raw);
        }
    }

    private Map<String, Object> map(Object raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private Set<String> strings(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw instanceof Collection<?> values) for (Object value : values) out.add(text(value));
        return out;
    }

    private int number(Object value) {
        assertTrue(value instanceof Number, "预期数字，实际为: " + value);
        return ((Number) value).intValue();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private void assertSafeRelative(String value, String message) {
        assertFalse(value.isBlank() || value.startsWith("/") || value.startsWith("\\") || value.contains(":")
                || value.contains(".."), message + "：" + value);
    }
}
