package com.xgls.web.utils;

import com.xgls.web.entity.InstanceDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetFormatUtilTest {
    @TempDir Path root;
    @Test void rejectsDotaLabelsEvenWithYamlAndDeclaredYolo() throws Exception {
        Files.writeString(root.resolve("data.yaml"), "names: [ship]");
        for (String split : new String[]{"train", "test"}) {
            Path dir = root.resolve("labels/" + split);
            Files.createDirectories(dir);
            Files.createDirectories(root.resolve("images/" + split));
            Files.writeString(root.resolve("images/" + split + "/a.png"), "image");
            Files.writeString(dir.resolve("a.txt"), "0 0 10 0 10 10 0 10 ship 0");
        }
        InstanceDataset data = new InstanceDataset();
        data.setParamSchema("{\"exportFormat\":\"yolo\"}");
        DatasetFormatUtil.inspect(data, root);
        assertEquals("yolo", data.getExportFormat());
        assertFalse(data.isYoloAvailable());
        assertNull(data.getDataYaml());
    }
    @Test void infersBothAndHonorsDeclaredFormat() throws Exception {
        Files.createDirectories(root.resolve("annotations"));
        Files.writeString(root.resolve("data.yaml"), "names: [ship]");
        for (String split : new String[]{"train", "test"}) {
            Files.writeString(root.resolve("annotations/" + split + ".json"),
                    "{\"images\":[{\"id\":1}],\"annotations\":[],\"categories\":[{\"id\":1}]}");
            Path dir = root.resolve("labels/" + split);
            Files.createDirectories(dir);
            Files.createDirectories(root.resolve("images/" + split));
            Files.writeString(root.resolve("images/" + split + "/a.png"), "image");
            Files.writeString(dir.resolve("a.txt"), "0 0.5 0.5 0.2 0.2");
        }
        InstanceDataset data = new InstanceDataset();
        DatasetFormatUtil.inspect(data, root);
        assertEquals("both", data.getExportFormat());
        assertTrue(data.isCocoAvailable());
        assertTrue(data.isYoloAvailable());
        assertTrue(data.getDataYaml().endsWith("data.yaml"));
        data.setParamSchema("{\"exportFormat\":\"coco\"}");
        DatasetFormatUtil.inspect(data, root);
        assertFalse(data.isYoloAvailable());
    }
}
