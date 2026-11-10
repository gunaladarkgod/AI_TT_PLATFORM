package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResearchRegistryServiceTest {
    @TempDir Path temp;

    private void file(String name, String content) throws Exception {
        Path path = temp.resolve("research-registry").resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private void validFixture() throws Exception {
        file("registry.yaml", "schema_version: 1\nname: test\nconventions:\n  - {id: dataset-format, title: 数据格式, path: conventions/dataset-format.md}\n");
        file("conventions/dataset-format.md", "# format\n");
        file("directions/small-object.yaml", "schema_version: 1\nid: small-object\nname: 小目标\nstatus: active\ndata_formats: [yolo]\nmetrics: [mAP]\nengine_compatibility: [{engine: ultralytics, versions: ['8.4.115'], data_formats: [yolo]}]\n");
        file("algorithms/small-object-yolo11n.yaml", "schema_version: 1\nid: small-object/yolo11n\nkind: baseline\nname: YOLO\ndirection: small-object\nstatus: draft\nrepository: {url: https://github.com/example/algo.git, default_ref: develop, stable_ref: null}\nplatform: {min_version: null}\nengine: {id: ultralytics, version: 8.4.115}\ndata_format: yolo\nalgorithm_manifest: research/small-object/yolo/algorithm.yaml\ndescription: test\n");
    }

    @Test
    void loadsDirectionsAlgorithmsAndConventionContent() throws Exception {
        validFixture();
        Map<String, Object> result = new ResearchRegistryService(temp).load();
        assertEquals(Boolean.TRUE, result.get("valid"));
        assertEquals(1, ((List<?>) result.get("directions")).size());
        assertEquals(1, ((List<?>) result.get("algorithms")).size());
        Map<?, ?> convention = (Map<?, ?>) ((List<?>) result.get("conventions")).get(0);
        assertEquals("# format\n", convention.get("content"));
    }

    @Test
    void reportsMissingFilesAndInvalidFields() throws Exception {
        file("registry.yaml", "schema_version: 2\nname: test\nconventions: []\n");
        file("directions/bad.yaml", "schema_version: 1\nid: Bad\nname: ''\nstatus: unknown\ndata_formats: [xml]\nmetrics: []\nengine_compatibility: []\n");
        file("algorithms/bad.yaml", "schema_version: 1\nid: bad/Bad\ndirection: missing\nstatus: published\nrepository: {url: http://example.com, default_ref: ''}\nplatform: {}\nengine: {id: unknown, version: ''}\ndata_format: xml\nalgorithm_manifest: ../secret\ndescription: ''\n");
        Map<String, Object> result = new ResearchRegistryService(temp).load();
        assertEquals(Boolean.FALSE, result.get("valid"));
        List<?> errors = (List<?>) result.get("errors");
        assertTrue(errors.stream().anyMatch(error -> String.valueOf(error).contains("schema_version")));
        assertTrue(errors.stream().anyMatch(error -> String.valueOf(error).contains("算法 id")));
        assertTrue(errors.stream().anyMatch(error -> String.valueOf(error).contains("repository.url")));
    }

    @Test
    void rejectsDuplicateAlgorithmIds() throws Exception {
        validFixture();
        file("algorithms/duplicate.yaml", Files.readString(temp.resolve("research-registry/algorithms/small-object-yolo11n.yaml")));
        Map<String, Object> result = new ResearchRegistryService(temp).load();
        assertTrue(((List<?>) result.get("errors")).stream().anyMatch(error -> String.valueOf(error).contains("算法 id 重复")));
    }

    @Test
    void validatesProjectRegistry() {
        Map<String, Object> result = new ResearchRegistryService().load();
        assertEquals(Boolean.TRUE, result.get("valid"), String.valueOf(result.get("errors")));
        List<?> directions = (List<?>) result.get("directions");
        assertEquals(3, directions.size());
        assertEquals(Set.of("B1:small-object", "B2:knowledge-distillation", "B3:spatiotemporal"),
                directions.stream().map(value -> {
                    Map<?, ?> direction = (Map<?, ?>) value;
                    return direction.get("code") + ":" + direction.get("id");
                }).collect(java.util.stream.Collectors.toSet()));
        assertEquals(12, ((List<?>) result.get("algorithms")).size());
        Map<?, ?> tdsc = ((List<Map<?, ?>>) result.get("algorithms")).stream()
                .filter(item -> "small-object/tdsc".equals(item.get("id"))).findFirst().orElseThrow();
        assertEquals("standalone-algorithm", tdsc.get("kind"));
        assertEquals("draft", tdsc.get("status"));
        Map<?, ?> baseline = (Map<?, ?>) ((Map<?, ?>) tdsc.get("derived_from")).get("baseline");
        assertEquals("small-object/yolo11", baseline.get("id"));
    }
}
