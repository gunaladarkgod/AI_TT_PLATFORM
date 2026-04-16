package com.xgls.web.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xgls.web.entity.InstanceDatasetMid;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceDatasetPathUtilTest {

    @Test
    void sourceOnDiskAcceptsNestedLabelsAndImages(@TempDir Path tmp) throws Exception {
        String task = "task_nested";
        Path base = tmp.resolve(task);
        Files.createDirectories(base.resolve("train/images/sub"));
        Files.createDirectories(base.resolve("train/anno/nested"));
        Files.createDirectories(base.resolve("test/images"));
        Files.createDirectories(base.resolve("test/anno"));
        Files.writeString(base.resolve("train/images/sub/a.jpg"), "x");
        Files.writeString(base.resolve("train/anno/nested/lab.txt"), "0 0 0 0 0");

        InstanceDatasetMid mid = new InstanceDatasetMid();
        mid.setName(task);
        mid.setFatherName(task);
        mid.setTrainImagePath(base.resolve("train/images").toString());
        mid.setTrainAnnoPath(base.resolve("train/anno").toString());
        mid.setTestImagePath(base.resolve("test/images").toString());
        mid.setTestAnnoPath(base.resolve("test/anno").toString());

        assertTrue(InstanceDatasetPathUtil.isSourceInstanceDatasetOnDisk(mid));
    }

    @Test
    void sourceOnDiskResolvesByMidRootWhenDbPathsStale(@TempDir Path tmp) throws Exception {
        String task = "exported_task";
        Path base = tmp.resolve(task);
        Files.createDirectories(base.resolve("train/images"));
        Files.createDirectories(base.resolve("train/anno"));
        Files.createDirectories(base.resolve("test/images"));
        Files.createDirectories(base.resolve("test/anno"));
        Files.writeString(base.resolve("train/images/p.png"), "x");
        Files.writeString(base.resolve("train/anno/a.json"), "{}");

        InstanceDatasetMid mid = new InstanceDatasetMid();
        mid.setName(task);
        mid.setFatherName(task);
        mid.setTrainImagePath("/nonexistent/old/train/images");
        mid.setTrainAnnoPath("/nonexistent/old/train/anno");
        mid.setTestImagePath("/nonexistent/old/test/images");
        mid.setTestAnnoPath("/nonexistent/old/test/anno");

        assertFalse(InstanceDatasetPathUtil.isSourceInstanceDatasetOnDisk(mid, null));
        assertTrue(InstanceDatasetPathUtil.isSourceInstanceDatasetOnDisk(mid, tmp.toString()));
    }

    @Test
    void sourceOnDiskFailsWhenNoLabels(@TempDir Path tmp) throws Exception {
        String task = "no_labels";
        Path base = tmp.resolve(task);
        Files.createDirectories(base.resolve("train/images"));
        Files.createDirectories(base.resolve("train/anno"));
        Files.createDirectories(base.resolve("test/images"));
        Files.createDirectories(base.resolve("test/anno"));
        Files.writeString(base.resolve("train/images/p.png"), "x");

        InstanceDatasetMid mid = new InstanceDatasetMid();
        mid.setName(task);
        mid.setTrainImagePath(base.resolve("train/images").toString());
        mid.setTrainAnnoPath(base.resolve("train/anno").toString());
        mid.setTestImagePath(base.resolve("test/images").toString());
        mid.setTestAnnoPath(base.resolve("test/anno").toString());

        assertFalse(InstanceDatasetPathUtil.isSourceInstanceDatasetOnDisk(mid));
    }
}
