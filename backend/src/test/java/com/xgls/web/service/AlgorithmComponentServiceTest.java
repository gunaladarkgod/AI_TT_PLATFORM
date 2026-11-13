package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmComponentServiceTest {
    @TempDir Path temp;

    private void file(String relative, String content) throws Exception {
        Path path = temp.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private void registry() throws Exception {
        file("research-registry/registry.yaml", """
                schema_version: 1
                name: test
                baselines:
                  - id: small-object/yolo11
                    direction: small-object
                    name: YOLO11
                    status: draft
                    repository: {url: https://github.com/example/yolo.git, ref: main}
                conventions: []
                """);
        file("research-registry/directions/small-object.yaml", """
                schema_version: 1
                id: small-object
                name: 小目标
                status: active
                data_formats: [yolo]
                metrics: [mAP]
                engine_compatibility: []
                """);
        file("research-registry/algorithms/small-object-tdsc.yaml", """
                schema_version: 1
                id: small-object/tdsc
                kind: standalone-algorithm
                name: TDSC
                direction: small-object
                status: draft
                repository: {url: https://github.com/example/tdsc.git, default_ref: main}
                platform: {min_version: null}
                engine: {id: ultralytics, version: 8.4.165}
                data_format: yolo
                algorithm_manifest: algorithm.yaml
                description: test
                """);
    }

    @Test
    void reportsDownloadedRepositoriesSeparatelyFromMissingPlatformManifests() throws Exception {
        registry();
        Files.createDirectories(temp.resolve("local-components/baselines/small-object/yolo11/.git"));
        Files.createDirectories(temp.resolve("local-components/algorithms/small-object/tdsc/.git"));
        ResearchRegistryService registry = new ResearchRegistryService(temp);
        AlgorithmComponentService service = new AlgorithmComponentService(temp, registry);
        try {
            Map<String, Object> baselines = service.list("baseline", null);
            Map<String, Object> algorithms = service.list("algorithm", null);
            Map<?, ?> baseline = (Map<?, ?>) ((List<?>) baselines.get("components")).get(0);
            Map<?, ?> algorithm = (Map<?, ?>) ((List<?>) algorithms.get("components")).get(0);
            assertEquals("downloaded", baseline.get("localStatus"));
            assertEquals("downloaded", algorithm.get("localStatus"));
            assertFalse((Boolean) algorithm.get("trainingReady"));
            assertTrue(((List<?>) algorithm.get("blockers")).contains("缺少 algorithm.yaml"));
            assertTrue(((List<?>) algorithm.get("blockers")).contains("缺少 derived-from.yaml"));
        } finally {
            service.close();
        }
    }
}
