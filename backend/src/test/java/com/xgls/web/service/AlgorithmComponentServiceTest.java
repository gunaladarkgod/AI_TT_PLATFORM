package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmComponentServiceTest {
    @TempDir Path root;

    @Test
    void listsRegisteredExternalAlgorithmAndExplainsWhyItCannotTrain() throws Exception {
        registry("small-object/tdsc", "draft");
        try (var service = new CloseableService(root)) {
            Map<String, Object> item = only(service.service.list(null));
            assertEquals("not-downloaded", item.get("localStatus"));
            assertFalse((Boolean) item.get("trainingReady"));
            assertTrue(((List<?>) item.get("blockers")).toString().contains("尚未下载"));
            assertTrue((Boolean) item.get("installAllowed"));
            assertEquals("not-connected", service.service.trainingProfile("small-object/tdsc").get("runnerIntegration"));
        }
    }

    @Test
    void validatesInstalledManifestBeforeMarkingComponentDownloaded() throws Exception {
        registry("small-object/tdsc", "published");
        Path installed = root.resolve("local-components/algorithms/small-object/tdsc");
        Files.createDirectories(installed);
        Files.writeString(installed.resolve("algorithm.yaml"), """
                id: small-object/tdsc
                engine:
                  id: ultralytics
                data_format: yolo
                entry:
                  train: train.py
                """);
        Files.writeString(installed.resolve("derived-from.yaml"), """
                baseline:
                  repository: https://example.invalid/baseline.git
                  ref: main
                  commit: abcdef1
                """);
        Files.writeString(installed.resolve("train.py"), "# fixture\n");
        git(installed, "init");
        git(installed, "config", "user.email", "test@example.invalid");
        git(installed, "config", "user.name", "Test");
        git(installed, "add", ".");
        git(installed, "commit", "-m", "fixture");
        try (var service = new CloseableService(root)) {
            Map<String, Object> item = only(service.service.list("small-object"));
            assertEquals("downloaded", item.get("localStatus"), item.toString());
            assertTrue((Boolean) item.get("trainingReady"));
            assertFalse(String.valueOf(item.get("sourceCommit")).isBlank());
        }
    }

    @Test
    void rejectsUnsafeOrUnregisteredIdsBeforeAnyGitOperation() throws Exception {
        registry("small-object/tdsc", "draft");
        try (var service = new CloseableService(root)) {
            assertThrows(IllegalArgumentException.class, () -> service.service.startInstall("../../outside"));
            assertThrows(IllegalArgumentException.class, () -> service.service.trainingProfile("small-object/unknown"));
        }
    }

    @Test
    void keepsBaselineDownloadsAndValidationSeparateFromCAlgorithms() throws Exception {
        baselineRegistry("small-object/yolo11-baseline", "draft");
        try (var service = new CloseableService(root)) {
            Map<String, Object> item = only(service.service.listBaselines("small-object"));
            assertEquals("baseline", item.get("kind"));
            assertEquals("not-downloaded", item.get("localStatus"));
            assertFalse((Boolean) item.get("baselineValidationReady"));
            assertTrue(((List<?>) item.get("blockers")).toString().contains("基线 B"));
            assertEquals("not-connected", service.service.baselineValidationProfile("small-object/yolo11-baseline").get("runnerIntegration"));
            assertThrows(IllegalArgumentException.class, () -> service.service.startInstall("small-object/yolo11-baseline"));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> only(Map<String, Object> response) {
        return (Map<String, Object>) ((List<?>) response.get("components")).get(0);
    }

    private void registry(String id, String status) throws Exception {
        Path dir = root.resolve("research-registry/algorithms");
        Files.createDirectories(dir);
        Path directions = root.resolve("research-registry/directions");
        Files.createDirectories(directions);
        Files.writeString(directions.resolve("small-object.yaml"), "schema_version: 1\nid: small-object\nname: test\n");
        Files.writeString(dir.resolve("tdsc.yaml"), """
                schema_version: 1
                id: %s
                name: TDSC
                kind: standalone-algorithm
                direction: small-object
                status: %s
                engine:
                  id: ultralytics
                  version: test
                data_format: yolo
                source:
                  type: external-git
                  repository: https://example.invalid/tdsc.git
                  default_ref: main
                  stable_ref: null
                  manifest: algorithm.yaml
                  derived_manifest: derived-from.yaml
                derived_from:
                  baseline:
                    repository: https://example.invalid/baseline.git
                    ref: main
                    commit: abcdef1
                description: test
                """.formatted(id, status));
    }

    private void baselineRegistry(String id, String status) throws Exception {
        Path dir = root.resolve("research-registry/algorithms");
        Files.createDirectories(dir);
        Path directions = root.resolve("research-registry/directions");
        Files.createDirectories(directions);
        Files.writeString(directions.resolve("small-object.yaml"), "schema_version: 1\nid: small-object\nname: test\n");
        Files.writeString(dir.resolve("baseline.yaml"), """
                schema_version: 1
                id: %s
                name: YOLO11 baseline
                kind: baseline
                direction: small-object
                status: %s
                engine:
                  id: ultralytics
                  version: test
                data_format: yolo
                source:
                  type: external-git
                  repository: https://example.invalid/baseline.git
                  default_ref: main
                  stable_ref: null
                  manifest: algorithm.yaml
                description: test
                """.formatted(id, status));
    }

    private void git(Path directory, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        int exit = process.waitFor();
        assertEquals(0, exit, new String(process.getInputStream().readAllBytes()));
    }

    private static class CloseableService implements AutoCloseable {
        final AlgorithmComponentService service;
        CloseableService(Path root) { service = new AlgorithmComponentService(root); }
        @Override public void close() { service.close(); }
    }
}
