package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmComponentServiceTest {
    @TempDir Path temp;

    @Test
    void springCreatesServiceWithRegistryDependency() throws Exception {
        registry();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ResearchRegistryService.class, () -> new ResearchRegistryService(temp));
            context.register(AlgorithmComponentService.class);
            context.refresh();

            Map<String, Object> result = context.getBean(AlgorithmComponentService.class).list("baseline", null);
            assertEquals(1, ((List<?>) result.get("components")).size());
        }
    }

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
                derived_from: {baseline: {id: small-object/yolo11}}
                platform: {min_version: null}
                engine: {id: ultralytics, version: 8.4.165}
                data_format: yolo
                algorithm_manifest: algorithm.yaml
                description: test
                """);
        file("research-registry/algorithms/small-object-improvement.yaml", """
                schema_version: 1
                id: small-object/legacy-improvement
                kind: improvement
                name: 旧改进包
                direction: small-object
                status: draft
                repository: {url: https://github.com/example/legacy.git, default_ref: main}
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
            assertEquals("small-object/yolo11", algorithm.get("baselineId"));
            assertEquals(1, ((List<?>) algorithms.get("components")).size());
            assertFalse((Boolean) algorithm.get("trainingReady"));
            assertTrue(((List<?>) algorithm.get("blockers")).contains("缺少 algorithm.yaml"));
            assertTrue(((List<?>) algorithm.get("blockers")).contains("缺少 derived-from.yaml"));
        } finally {
            service.close();
        }
    }

    @Test
    void reportsAndRemovesAnIncompleteLocalAlgorithmCheckout() throws Exception {
        registry();
        Path target = temp.resolve("local-components/algorithms/small-object/tdsc");
        Files.createDirectories(target.resolve("src"));
        Files.writeString(target.resolve("src/algorithm.py"), "partial checkout");

        AlgorithmComponentService service = new AlgorithmComponentService(temp, new ResearchRegistryService(temp));
        try {
            Map<?, ?> component = (Map<?, ?>) ((List<?>) service.list("algorithm", null).get("components")).get(0);
            assertEquals("partial", component.get("localStatus"));
            assertFalse((Boolean) component.get("trainingReady"));

            service.remove("algorithm", "small-object/tdsc");
            awaitTask(service);

            assertEquals("succeeded", service.snapshot().get("status"));
            assertFalse(Files.exists(target));
        } finally {
            service.close();
        }
    }

    @Test
    void removesReadOnlyGitPackFilesWhenDosAttributesAreSupported() throws Exception {
        registry();
        Path target = temp.resolve("local-components/algorithms/small-object/tdsc");
        Files.createDirectories(target.resolve(".git/objects/pack"));
        Path packFile = target.resolve(".git/objects/pack/pack-test.idx");
        Files.writeString(packFile, "read only pack index");
        DosFileAttributeView dos = Files.getFileAttributeView(packFile, DosFileAttributeView.class);
        Assumptions.assumeTrue(dos != null, "filesystem does not expose DOS attributes");
        dos.setReadOnly(true);

        AlgorithmComponentService service = new AlgorithmComponentService(temp, new ResearchRegistryService(temp));
        try {
            service.remove("algorithm", "small-object/tdsc");
            awaitTask(service);

            assertEquals("succeeded", service.snapshot().get("status"));
            assertFalse(Files.exists(target));
        } finally {
            service.close();
        }
    }

    private void awaitTask(AlgorithmComponentService service) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while ("running".equals(service.snapshot().get("status")) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertNotEquals("running", service.snapshot().get("status"), "component removal timed out");
    }
}
