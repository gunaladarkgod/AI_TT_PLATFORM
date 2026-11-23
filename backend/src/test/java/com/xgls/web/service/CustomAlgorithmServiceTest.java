package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CustomAlgorithmServiceTest {
    private static final String YAML = "backbone: [[-1, 1, Conv, [16]]]\nhead: [[-1, 1, Detect, [1]]]\n";
    @TempDir Path workspace;

    @Test
    void singleModelPublishesWithoutAnIntermediateRecord() throws Exception {
        CustomAlgorithmService service = new CustomAlgorithmService(workspace);
        Map<String, Object> uploaded = service.upload(1L, "small-object", "", "本地算法", zip(Map.of(
                "ultralytics/__init__.py", "__version__ = '8.4.165'",
                "model.yaml", YAML)));
        assertTrue((Boolean) uploaded.get("trainingReady"));
        assertEquals("开发中", uploaded.get("registrationStatus"));
        assertEquals("待登记", uploaded.get("baselineId"));
        assertTrue(Files.isRegularFile(workspace.resolve(uploaded.get("path").toString()).resolve("training.json")));
        assertEquals(uploaded.get("id"), service.list("small-object").get(0).get("id"));
    }

    @Test
    void multipleModelsAreInvisibleUntilTheOwnerConfirms() throws Exception {
        CustomAlgorithmService service = new CustomAlgorithmService(workspace);
        Map<String, Object> prepared = service.upload(1L, "small-object", "small-object/yolo11", "custom", multiModelZip());
        String token = prepared.get("uploadToken").toString();
        assertTrue(service.list(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.complete(2L, token, "other.yaml", ""));
        assertTrue(service.list(null).isEmpty());
        Map<String, Object> uploaded = service.complete(1L, token, "other.yaml", "");
        assertEquals("small-object/yolo11", uploaded.get("baselineId"));
        assertTrue((Boolean) uploaded.get("trainingReady"));
        assertEquals(1, service.list(null).size());
    }

    @Test
    void invalidOrCancelledUploadsDoNotLeaveARecordOrStagingFiles() throws Exception {
        CustomAlgorithmService service = new CustomAlgorithmService(workspace);
        MockMultipartFile corrupt = new MockMultipartFile("file", "broken.zip", "application/zip", new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> service.upload(1L, "small-object", "", "bad", corrupt));
        assertThrows(IllegalArgumentException.class, () -> service.upload(1L, "small-object", "", "bad", zip(Map.of("../escape.txt", "bad"))));
        var prepared = service.upload(1L, "small-object", "", "wrong model", multiModelZip());
        assertThrows(IllegalArgumentException.class,
                () -> service.complete(1L, prepared.get("uploadToken").toString(), "../outside.yaml", ""));
        var cancelled = service.upload(1L, "small-object", "", "cancelled", multiModelZip());
        service.cancel(1L, cancelled.get("uploadToken").toString());
        assertTrue(service.list(null).isEmpty());
        assertFalse(Files.exists(workspace.resolve("escape.txt")));
        try (var files = Files.list(workspace.resolve("local-components/custom-algorithms/.staging"))) {
            assertEquals(0, files.count());
        }
    }

    private MockMultipartFile multiModelZip() throws Exception {
        return zip(Map.of(
                "wrap/ultralytics/__init__.py", "__version__ = \"8.4.165\"",
                "wrap/model.yaml", YAML,
                "wrap/other.yaml", YAML));
    }

    private MockMultipartFile zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "algorithm.zip", "application/zip", bytes.toByteArray());
    }
}
