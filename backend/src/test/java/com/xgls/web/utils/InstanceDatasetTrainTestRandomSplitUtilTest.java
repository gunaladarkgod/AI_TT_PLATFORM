package com.xgls.web.utils;

import com.xgls.web.entity.InstanceDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceDatasetTrainTestRandomSplitUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void splitsPairedImagesIntoTrainAndTest() throws Exception {
        InstanceDataset dataset = datasetWithLayout("task", "output");
        Path base = tempDir.resolve("task/output");
        for (int i = 0; i < 10; i++) {
            Files.writeString(base.resolve("images/train/img_" + i + ".jpg"), "image");
            Files.writeString(base.resolve("annotations/train/img_" + i + ".txt"), "0 0 0 1 0 1 1 0 1 0");
        }

        var result = InstanceDatasetTrainTestRandomSplitUtil.run(dataset, tempDir.toString(), 0.8);

        assertEquals(8, result.trainImages());
        assertEquals(2, result.testImages());
        assertEquals(8, InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(base.resolve("annotations/train")));
        assertEquals(2, InstanceDatasetTrainTestRandomSplitUtil.countAnnoLabelFiles(base.resolve("annotations/test")));
    }

    @Test
    void rejectsImageWithoutMatchingAnnotation() throws Exception {
        InstanceDataset dataset = datasetWithLayout("task", "broken");
        Path base = tempDir.resolve("task/broken");
        Files.writeString(base.resolve("images/train/a.jpg"), "image");
        Files.writeString(base.resolve("images/train/b.jpg"), "image");
        Files.writeString(base.resolve("annotations/train/a.txt"), "label");

        assertThrows(IllegalStateException.class,
                () -> InstanceDatasetTrainTestRandomSplitUtil.run(dataset, tempDir.toString(), 0.8));
    }

    private InstanceDataset datasetWithLayout(String father, String name) throws Exception {
        Path base = tempDir.resolve(father).resolve(name);
        Files.createDirectories(base.resolve("images/train"));
        Files.createDirectories(base.resolve("images/test"));
        Files.createDirectories(base.resolve("annotations/train"));
        Files.createDirectories(base.resolve("annotations/test"));
        InstanceDataset dataset = new InstanceDataset();
        dataset.setFatherName(father);
        dataset.setName(name);
        dataset.setTrainImagePath(father + "/" + name + "/images/train/");
        dataset.setTestImagePath(father + "/" + name + "/images/test/");
        dataset.setTrainAnnoPath(father + "/" + name + "/annotations/train/");
        dataset.setTestAnnoPath(father + "/" + name + "/annotations/test/");
        return dataset;
    }
}
