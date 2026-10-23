package com.xgls.web.service;

import com.xgls.web.entity.TrainResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainResultArtifactServiceTest {
    @TempDir Path workspace;

    @Test
    void importedLabelDismissalPersistsWithoutChangingSource() throws Exception {
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", workspace.toString());
        try {
            Files.createDirectories(workspace.resolve("backend"));
            Files.createDirectories(workspace.resolve("fronternd"));
            Path resultDir = workspace.resolve("artifacts/yolo_runs/imported/sample");
            Files.createDirectories(resultDir);
            TrainResult result = new TrainResult();
            result.setId(73);
            result.setTaskName("sample");
            TrainResultArtifactService service = new TrainResultArtifactService();
            service.createImportedSnapshot(result, "ultralytics", "sample", resultDir);
            service.mergeMetadata(result);
            assertTrue(result.getExternalImported());
            assertFalse(result.getImportLabelHidden());

            service.hideImportLabel(result);
            TrainResult reloaded = new TrainResult();
            reloaded.setId(73);
            reloaded.setTaskName("sample");
            service.mergeMetadata(reloaded);
            assertTrue(reloaded.getExternalImported());
            assertTrue(reloaded.getImportLabelHidden());
            assertEquals("sample", reloaded.getResultName());
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test
    void nativeResultCannotDismissImportLabel() {
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", workspace.toString());
        try {
            Files.createDirectories(workspace.resolve("backend"));
            Files.createDirectories(workspace.resolve("fronternd"));
            TrainResult result = new TrainResult();
            result.setId(74);
            result.setTaskName("native");
            assertThrows(IllegalStateException.class,
                    () -> new TrainResultArtifactService().hideImportLabel(result));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
