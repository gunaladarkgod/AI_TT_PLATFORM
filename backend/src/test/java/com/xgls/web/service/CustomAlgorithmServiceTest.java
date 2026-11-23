package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CustomAlgorithmServiceTest {
    @TempDir Path temp;

    private MockMultipartFile zip(String entryName, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes());
            zip.closeEntry();
        }
        return new MockMultipartFile("file", "algorithm.zip", "application/zip", bytes.toByteArray());
    }

    @Test
    void uploadsLocalAlgorithmAndKeepsItInPendingRegistrationState() throws Exception {
        CustomAlgorithmService service = new CustomAlgorithmService(temp);
        Map<String, Object> result = service.upload("small-object", "", "TDSC 本地版本", zip("scripts/train.py", "print('ok')"));
        assertEquals("uploaded", result.get("localStatus"));
        assertEquals("待登记", result.get("registrationStatus"));
        assertEquals("待登记", result.get("baselineId"));
        assertTrue(Files.isRegularFile(temp.resolve("local-components/custom-algorithms/small-object").resolve(Path.of(String.valueOf(result.get("id")).substring("custom/small-object/".length()), "scripts/train.py"))));
        List<Map<String, Object>> listed = service.list("small-object");
        assertEquals(1, listed.size());
        assertEquals(result.get("id"), listed.get(0).get("id"));
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        CustomAlgorithmService service = new CustomAlgorithmService(temp);
        assertThrows(IllegalArgumentException.class, () -> service.upload("small-object", "small-object/yolo11", "bad", zip("../escape.txt", "bad")));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }
}
