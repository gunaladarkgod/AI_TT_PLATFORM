package com.xgls.web.utils;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatasetAnnotationExportUtilTest {
    @TempDir Path root;

    @Test
    void exportsCocoAndNormalizedYoloLabelsAndRefreshesLabels() throws Exception {
        for (String split : List.of("train", "test")) {
            Path images = root.resolve("images").resolve(split);
            Path annotations = root.resolve("annotations").resolve(split);
            Files.createDirectories(images);
            Files.createDirectories(annotations);
            ImageIO.write(new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB), "png",
                    images.resolve("sample.png").toFile());
            Files.writeString(annotations.resolve("sample.txt"), "10 5 50 5 50 25 10 25 ship 0\n");
        }

        DatasetAnnotationExportUtil.export(root, List.of("ship"), "both");
        var coco = JSONUtil.parseObj(Files.readString(root.resolve("annotations/train.json")));
        assertEquals(1, coco.getJSONArray("images").size());
        assertEquals(1, coco.getJSONArray("annotations").size());
        assertEquals(1, coco.getJSONArray("categories").size());
        assertEquals(1, coco.getJSONArray("annotations").getJSONObject(0).getInt("category_id"));
        assertEquals("0 0.30000000 0.30000000 0.40000000 0.40000000",
                Files.readString(root.resolve("labels/train/sample.txt")).trim());

        Files.writeString(root.resolve("labels/train/stale.txt"), "stale");
        DatasetAnnotationExportUtil.export(root, List.of("ship"), "yolo");
        assertFalse(Files.exists(root.resolve("labels/train/stale.txt")));
    }

    @Test
    void cocoOnlyDoesNotCreateYoloLabels() throws Exception {
        for (String split : List.of("train", "test")) {
            Path images = root.resolve("images").resolve(split);
            Path annotations = root.resolve("annotations").resolve(split);
            Files.createDirectories(images);
            Files.createDirectories(annotations);
            ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png",
                    images.resolve("sample.png").toFile());
            Files.writeString(annotations.resolve("sample.txt"), "0 0 10 0 10 10 0 10 ship 0\n");
        }
        DatasetAnnotationExportUtil.export(root, List.of("ship"), "coco");
        assertTrue(Files.isRegularFile(root.resolve("annotations/train.json")));
        assertFalse(Files.exists(root.resolve("labels")));
        assertThrows(IllegalArgumentException.class,
                () -> DatasetAnnotationExportUtil.normalizeFormat("unknown"));
    }

    @Test
    void yoloOnlyDoesNotCreateCocoJson() throws Exception {
        for (String split : List.of("train", "test")) {
            Path images = root.resolve("images").resolve(split);
            Path annotations = root.resolve("annotations").resolve(split);
            Files.createDirectories(images);
            Files.createDirectories(annotations);
            ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png",
                    images.resolve("sample.png").toFile());
            Files.writeString(annotations.resolve("sample.txt"), "0 0 10 0 10 10 0 10 ship 0\n");
        }
        DatasetAnnotationExportUtil.export(root, List.of("ship"), "yolo");
        assertTrue(Files.isRegularFile(root.resolve("labels/train/sample.txt")));
        assertFalse(Files.exists(root.resolve("annotations/train.json")));
    }
}
