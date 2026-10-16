package com.xgls.web.service;

import com.xgls.web.entity.TrainResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class TrainResultPackageServiceTest {
    @TempDir Path directory;
    private final TrainResultPackageService service = new TrainResultPackageService(null, null);

    @Test
    void acceptsWrappedUltralyticsRun() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("yolo11n_visdrone_baseline/results.csv",
                "epoch,train/box_loss,metrics/mAP50(B)\n1,1.2,0.3\n");
        files.put("yolo11n_visdrone_baseline/weights/best.pt", "checkpoint");
        service.extract(zip(files), directory);

        Path root = service.packageRoot(directory);
        assertEquals("yolo11n_visdrone_baseline", root.getFileName().toString());
        service.validateYolo(root.resolve("results.csv"));
        assertTrue(Files.isRegularFile(root.resolve("weights/best.pt")));
    }

    @Test
    void rejectsZipPathTraversal() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service.extract(zip(Map.of("../escaped.csv", "content")), directory));
    }

    @Test
    void keepsVisDataDirectoryForMmdetParser() throws Exception {
        service.extract(zip(Map.of("vis_data/scalars.json", "{\"epoch\":1,\"step\":10,\"loss\":2.1}\n")), directory);
        Path root = service.packageRoot(directory);
        assertEquals(directory, root);
        service.validateMmdet(root);
    }

    @Test
    void importsAndExportsNativeUltralyticsPackage() throws Exception {
        TrainResultService results = mock(TrainResultService.class);
        TrainResultArtifactService artifacts = mock(TrainResultArtifactService.class);
        when(results.save(any(TrainResult.class))).thenAnswer(call -> {
            ((TrainResult) call.getArgument(0)).setId(42);
            return true;
        });
        TrainResultPackageService packageService = new TrainResultPackageService(results, artifacts, directory);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("yolo11n_visdrone_baseline/results.csv",
                "epoch,train/box_loss,metrics/mAP50(B),metrics/mAP50-95(B)\n1,1.2,0.3,0.2\n");
        files.put("yolo11n_visdrone_baseline/weights/best.pt", "checkpoint");

        TrainResult imported = packageService.importPackage(zip(files), "alice");
        assertEquals("alice", imported.getUserName());
        assertEquals(0.2, imported.getMap());
        verify(artifacts).createImportedSnapshot(any(TrainResult.class),
                org.mockito.ArgumentMatchers.eq("ultralytics"),
                org.mockito.ArgumentMatchers.eq("result"), any(Path.class));

        try (var paths = Files.walk(directory.resolve("yolo_runs/imported"))) {
            Path run = paths.filter(path -> Files.isRegularFile(path.resolve("results.csv"))).findFirst().orElseThrow();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            packageService.exportPackage(run, output);
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
                Set<String> names = new java.util.HashSet<>();
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
                assertTrue(names.contains("results.csv"));
                assertTrue(names.contains("weights/best.pt"));
            }
        }
    }

    private MockMultipartFile zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (var file : files.entrySet()) {
                output.putNextEntry(new ZipEntry(file.getKey()));
                output.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return new MockMultipartFile("file", "result.zip", "application/zip", bytes.toByteArray());
    }
}
