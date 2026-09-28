package com.xgls.web.service;

import cn.hutool.json.JSONObject;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalTrainResultServiceTest {
    @TempDir Path workspace;
    String oldDir;
    TrainResultArtifactService artifacts;
    LocalTrainResultService service;

    @BeforeEach void setup() throws Exception {
        oldDir = System.getProperty("user.dir");
        Files.createDirectories(workspace.resolve("backend"));
        Files.createDirectories(workspace.resolve("fronternd"));
        System.setProperty("user.dir", workspace.toString());
        artifacts = new TrainResultArtifactService();
        service = new LocalTrainResultService(artifacts);
    }
    @AfterEach void restore() { System.setProperty("user.dir", oldDir); }
    Path run(String engine, String stamp) throws Exception {
        Path dir = workspace.resolve("artifacts/" + engine + "/demo_from_pyserver_sync_20260927_" + stamp + "_123456");
        Files.createDirectories(dir);
        return dir;
    }
    TrainTask task() {
        TrainTask task = new TrainTask(); task.setId(33); task.setName("demo"); task.setUsername("owner"); return task;
    }
    @Test void interruptedRunIsDiscoveredWithoutDatabaseAndKeepsEditsAndStableId() throws Exception {
        Path dir = run("mmdet_runs", "181514");
        Files.writeString(dir.resolve("train.log"), "coco/bbox_mAP: 0.1 coco/bbox_mAP_50: 0.2\ncoco/bbox_mAP: 0.3 coco/bbox_mAP_50: 0.4\n");
        Files.writeString(dir.resolve("epoch_50.pth"), "weight");
        TrainResult first = service.list(List.of(task()), List.of()).get(0);
        assertTrue(first.getId() < 0); assertEquals(0.3, first.getMap()); assertEquals(0.4, first.getAp50());
        assertEquals("unknown", first.getRunStatus()); assertEquals("owner", first.getUserName());
        assertEquals(1, artifacts.readMeta(first.getId()).getJSONArray("weights").size());
        artifacts.updateMetadata(first, "我的模型", "保留的备注", List.of("断训"));
        TrainResult second = service.list(List.of(task()), List.of()).get(0);
        assertEquals(first.getId(), second.getId()); assertEquals("我的模型", second.getResultName());
        assertEquals("保留的备注", second.getRemark()); assertEquals(List.of("断训"), second.getTags());
        assertEquals(dir.toString(), artifacts.resultDirectory(first.getId()));
    }
    @Test void repeatedTaskRunsHaveDistinctLogsAndIdsAndNestedInferenceIsNotAResult() throws Exception {
        Path a = run("mmdet_runs", "181514"), b = run("mmdet_runs", "191514");
        Files.writeString(a.resolve("train.log"), "first"); Files.writeString(b.resolve("train.log"), "second");
        Files.createDirectories(a.resolve("inference/demo_from_pyserver_sync_20260927_201514_123456"));
        List<TrainResult> rows = service.list(List.of(task()), List.of());
        assertEquals(2, rows.size()); assertNotEquals(rows.get(0).getId(), rows.get(1).getId());
        Set<Object> logs = new HashSet<>(); for (TrainResult r : rows) logs.add(service.log(r.getId()).get("content"));
        assertEquals(Set.of("first", "second"), logs);
    }
    @Test void migratesOldAbsoluteMetadataPathWithoutDuplicatingDatabaseResult() throws Exception {
        Path dir = run("mmdet_runs", "181514");
        Files.writeString(dir.resolve("config.py"), "model = dict()");
        TrainResult old = new TrainResult(); old.setId(36); old.setTaskName("demo"); old.setUserName("owner");
        artifacts.writeMeta(new JSONObject().set("resultId", 36).set("resultName", "old name")
                .set("resultDir", "/old/device/artifacts/mmdet_runs/" + dir.getFileName()));
        List<TrainResult> rows = service.list(List.of(task()), List.of(old));
        assertEquals(1, rows.size()); assertEquals(36, rows.get(0).getId()); assertEquals("old name", rows.get(0).getResultName());
        assertEquals("model = dict()", artifacts.readConfigSnapshot(rows.get(0), true).get("text"));
    }
    @Test void readsUltralyticsCsvAndIgnoresIncompleteLastRow() throws Exception {
        Path dir = run("yolo_runs/research/small/base", "181514");
        Files.writeString(dir.resolve("results.csv"), "epoch,metrics/mAP50(B),metrics/mAP50-95(B)\n1,0.4,0.2\n2,0.6,0.3\n3,0.");
        Files.writeString(dir.resolve("run_status.json"), "{\"status\":\"completed\"}");
        TrainResult row = service.list(List.of(), List.of()).get(0);
        assertEquals("Ultralytics", row.getModelType()); assertEquals(0.6, row.getAp50()); assertEquals(0.3, row.getMap());
        assertEquals("completed", row.getRunStatus()); assertFalse(row.getTraining());
    }
    @Test void runningAndDeadProcessesHaveDifferentStatus() throws Exception {
        Path dir = run("mmdet_runs", "181514");
        Files.writeString(dir.resolve("run_status.json"), "{\"status\":\"running\",\"pid\":" + ProcessHandle.current().pid() + "}");
        TrainResult row = service.list(List.of(), List.of()).get(0);
        assertTrue(row.getTraining());
        Files.writeString(dir.resolve("run_status.json"), "{\"status\":\"running\",\"pid\":2147483647}");
        assertFalse(service.get(row.getId()).getTraining());
    }
    @Test void ignoresSymlinkedRunOutsideArtifactRoot() throws Exception {
        Path external = Files.createDirectories(workspace.resolve("external"));
        Path base = Files.createDirectories(workspace.resolve("artifacts/mmdet_runs"));
        Files.createSymbolicLink(base.resolve("demo_from_pyserver_sync_20260927_181514_123456"), external);
        assertTrue(service.list(List.of(), List.of()).isEmpty());
    }
}
