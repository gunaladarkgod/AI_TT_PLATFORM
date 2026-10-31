package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceComponentServiceTest {
    @TempDir Path temp;
    private void file(Path root, String path) throws Exception {
        Path target = root.resolve(path); Files.createDirectories(target.getParent()); Files.writeString(target, "fixture");
    }
    private void git(Path cwd, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git")); command.addAll(List.of(args));
        Process p = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(p.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, p.exitValue(), command.toString());
    }
    private Path checkout() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        file(source, "README.md");
        file(source, "backend/keep.txt");
        file(source, "engines/yolo_run/ultralytics_runner/requirements.txt");
        file(source, "engines/mmdet_run/unused.txt");
        for (String required : List.of("mmdetection-3.0.0/mmdet/__init__.py", "mmdetection-3.0.0/tools/train.py",
            "myfiles/template/rcnn/faster r-cnn.py", "myfiles/template/detr/detr.py")) file(source, "engines/mmdet_run/" + required);
        file(source, "research/small-object/direction.yaml");
        for (String baseline : List.of("ultralytics-yolo11n-v1", "ultralytics-yolov5s-v1", "ultralytics-yolov5m-v1", "mmdet-faster-rcnn-r50-v1"))
            file(source, "research/small-object/" + baseline + "/algorithm.yaml");
        git(source, "init"); git(source, "add", ".");
        git(source, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-m", "fixture");
        git(temp, "clone", "--sparse", source.toString(), "checkout");
        Path root = temp.resolve("checkout"); git(root, "sparse-checkout", "set", "--cone", "backend");
        return root;
    }
    private Map<String, Object> finish(WorkspaceComponentService service) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while ("running".equals(service.snapshot().get("status")) && System.nanoTime() < deadline) Thread.sleep(20);
        return service.snapshot();
    }
    @Test void downloadsOnlySelectedEngineAndDirectionAndPreservesExistingSelection() throws Exception {
        Path root = checkout();
        try (var ignored = new AutoCloseableService(root)) {
            WorkspaceComponentService service = ignored.service;
            assertEquals("missing", service.component("ultralytics", "ultralytics").get("status"));
            service.start("small-object", "ultralytics");
            assertEquals("succeeded", finish(service).get("status"), service.snapshot().toString());
            assertTrue(Files.exists(root.resolve("backend/keep.txt")));
            assertFalse(Files.exists(root.resolve("engines/mmdet_run/unused.txt")));
            assertFalse(Files.exists(root.resolve("research/small-object/mmdet-faster-rcnn-r50-v1")));
            assertEquals("ready", service.component("small-object", "ultralytics").get("status"));
            Files.delete(root.resolve("engines/yolo_run/ultralytics_runner/requirements.txt"));
            assertEquals("partial", service.component("ultralytics", "ultralytics").get("status"));
            service.start("ultralytics", "ultralytics");
            assertEquals("failed", finish(service).get("status"));
            assertFalse(Files.exists(root.resolve("engines/yolo_run/ultralytics_runner/requirements.txt")));
        }
    }
    @Test void rejectsUnknownComponentsAndNonGitWorkspaces() {
        WorkspaceComponentService service = new WorkspaceComponentService(temp);
        try {
            assertThrows(IllegalArgumentException.class, () -> service.start("../../secret", "ultralytics"));
            assertThrows(IllegalArgumentException.class, () -> service.start("ultralytics", null));
            assertThrows(IllegalArgumentException.class, () -> service.start("ultralytics", "ultralytics"));
            assertEquals(false, service.list("ultralytics").get("downloadSupported"));
        } finally { service.close(); }
    }
    @Test void mmdetDirectionDoesNotDownloadUltralyticsAndAbsentBranchContentFails() throws Exception {
        Path root = checkout();
        try (var holder = new AutoCloseableService(root)) {
            var service = holder.service;
            service.start("small-object", "mmdet");
            assertEquals("succeeded", finish(service).get("status"), service.snapshot().toString());
            assertEquals("ready", service.component("mmdet", "mmdet").get("status"));
            assertFalse(Files.exists(root.resolve("engines/yolo_run/ultralytics_runner")));
            assertFalse(Files.exists(root.resolve("research/small-object/ultralytics-yolo11n-v1")));
            service.start("knowledge-distillation", "mmdet");
            assertEquals("failed", finish(service).get("status"));
        }
    }
    @Test void fullCheckoutIsNeverConvertedToSparse() throws Exception {
        Path root = checkout(); git(root, "sparse-checkout", "disable");
        WorkspaceComponentService service = new WorkspaceComponentService(root);
        try { assertThrows(IllegalArgumentException.class, () -> service.start("ultralytics", "ultralytics")); }
        finally { service.close(); }
        assertTrue(Files.exists(root.resolve("engines/mmdet_run/unused.txt")));
    }
    @Test void removeDirectionThenEngineAndDownloadEngineAgain() throws Exception {
        Path root = checkout();
        try (var holder = new AutoCloseableService(root)) {
            var service = holder.service;
            service.start("small-object", "ultralytics");
            assertEquals("succeeded", finish(service).get("status"));
            service.remove("ultralytics", "ultralytics");
            assertEquals("failed", finish(service).get("status"));
            assertTrue(Files.exists(root.resolve("engines/yolo_run/ultralytics_runner/requirements.txt")));
            service.remove("small-object", "ultralytics");
            assertEquals("succeeded", finish(service).get("status"), service.snapshot().toString());
            assertFalse(Files.exists(root.resolve("research/small-object/ultralytics-yolo11n-v1")));
            assertTrue(Files.exists(root.resolve("engines/yolo_run/ultralytics_runner/requirements.txt")));
            service.remove("ultralytics", "ultralytics");
            assertEquals("succeeded", finish(service).get("status"));
            assertFalse(Files.exists(root.resolve("engines/yolo_run/ultralytics_runner")));
            assertTrue(Files.exists(root.resolve("backend/keep.txt")));
            service.start("ultralytics", "mmdet"); // Engine row determines its own dependency, regardless of direction filter.
            assertEquals("succeeded", finish(service).get("status"));
            assertFalse(Files.exists(root.resolve("research/small-object")));
        }
    }
    @Test void removalProtectsEditsIgnoredFilesAndBroadSelections() throws Exception {
        Path root = checkout();
        try (var holder = new AutoCloseableService(root)) {
            var service = holder.service;
            service.start("ultralytics", "ultralytics");
            assertEquals("succeeded", finish(service).get("status"));
            Path requirement = root.resolve("engines/yolo_run/ultralytics_runner/requirements.txt");
            Files.writeString(requirement, "local edit");
            service.remove("ultralytics", "ultralytics");
            assertEquals("failed", finish(service).get("status"));
            assertEquals("local edit", Files.readString(requirement));
            Files.writeString(requirement, "fixture");
            Files.writeString(root.resolve(".git/info/exclude"), "*.cache\n");
            Path cache = root.resolve("engines/yolo_run/ultralytics_runner/local.cache");
            Files.writeString(cache, "personal runtime file");
            service.remove("ultralytics", "ultralytics");
            assertEquals("failed", finish(service).get("status"));
            assertTrue(Files.exists(cache));
            Files.delete(cache);
            git(root, "sparse-checkout", "add", "engines");
            service.remove("ultralytics", "ultralytics");
            assertEquals("failed", finish(service).get("status"));
            assertTrue(Files.exists(requirement));
        }
    }
    private static class AutoCloseableService implements AutoCloseable {
        final WorkspaceComponentService service;
        AutoCloseableService(Path root) { service = new WorkspaceComponentService(root); }
        public void close() { service.close(); }
    }
    @Test void missingEngineParentWarningIsNotTreatedAsLocalModification() throws Exception {
        Path root = checkout();
        // Platform startup creates legacy MMDet runtime directories without checking out an engine.
        Files.createDirectories(root.resolve("engines/mmdet_run/myfiles/src/task"));
        assertFalse(Files.exists(root.resolve("engines/yolo_run")));
        try (var holder = new AutoCloseableService(root)) {
            holder.service.start("ultralytics", "ultralytics");
            assertEquals("succeeded", finish(holder.service).get("status"), holder.service.snapshot().toString());
            assertTrue(Files.isRegularFile(root.resolve("engines/yolo_run/ultralytics_runner/requirements.txt")));
        }
    }
}
