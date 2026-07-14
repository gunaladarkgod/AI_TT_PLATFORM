package com.xgls.web.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreprocessCocoCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void expandsLegacyCocoIntoOneDotaFilePerImage() throws Exception {
        Path images = tempDir.resolve("images");
        Path annotations = tempDir.resolve("annotations");
        Files.createDirectories(images);
        Files.createDirectories(annotations);
        Files.writeString(images.resolve("sample.jpg"), "image");
        Files.writeString(annotations.resolve("instances.json"), """
                {"images":[{"id":1,"file_name":"sample.jpg"}],
                 "categories":[{"id":3,"name":"ship"}],
                 "annotations":[
                   {"id":1,"image_id":1,"category_id":3,"bbox":[10,20,30,40],"segmentation":[]},
                   {"id":2,"image_id":1,"category_id":3,"bbox":[50,60,10,20],"segmentation":[]}
                 ]}
                """);

        Method method = PreprocessServiceImpl.class.getDeclaredMethod(
                "materializeCocoAnnotations", Path.class, Path.class);
        method.setAccessible(true);
        method.invoke(new PreprocessServiceImpl(), images, annotations);

        Path dota = annotations.resolve("sample.txt");
        assertTrue(Files.isRegularFile(dota));
        assertEquals(2, Files.readAllLines(dota).size());
        assertTrue(Files.readString(dota).contains("ship 0"));
    }
}
