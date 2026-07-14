package com.xgls.web.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TrainConfigPathUtilTest {
    @TempDir Path tempDir;

    @Test
    void resolvesChineseTaskNameUnderNewModelcfgOnly() throws Exception {
        Path expected = tempDir.resolve("modelcfg/快速任务_20260714_1917/config.py");
        Files.createDirectories(expected.getParent());
        Files.writeString(expected, "model = dict(type='Detector')");

        assertEquals(expected.toAbsolutePath().normalize(),
                TrainConfigPathUtil.findExistingConfig(tempDir, "快速任务_20260714_1917"));
    }

    @Test
    void rejectsPathTraversalTaskName() {
        assertNull(TrainConfigPathUtil.resolveConfig(tempDir, "../outside"));
    }
}
