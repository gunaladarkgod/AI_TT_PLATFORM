package com.xgls.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingResultImageServiceTest {
    @TempDir Path directory;
    private final TrainingResultImageService service = new TrainingResultImageService();

    @Test
    void discoversStandardImagesAndSelectsSixFeaturedDiagnostics() throws Exception {
        for (String name : new String[]{
                "results.png", "confusion_matrix.png", "confusion_matrix_normalized.png",
                "BoxPR_curve.png", "BoxF1_curve.png", "labels.jpg",
                "val_batch0_labels.jpg", "val_batch0_pred.jpg", "val_batch1_pred.jpg",
                "train_batch0.jpg", "train_batch72812.jpg", "notes.png"}) {
            Files.writeString(directory.resolve(name), name);
        }

        var images = service.list(directory);
        assertEquals(11, images.size());
        assertEquals(6, images.stream().filter(TrainingResultImageService.TrainingImage::featured).count());
        assertTrue(images.stream().anyMatch(image -> image.name().equals("train_batch72812.jpg") && image.featured()));
        assertTrue(images.stream().noneMatch(image -> image.name().equals("notes.png")));
    }

    @Test
    void onlyResolvesWhitelistedTopLevelImages() throws Exception {
        Files.writeString(directory.resolve("BoxPR_curve.png"), "image");
        assertEquals("BoxPR_curve.png", service.resolve(directory, "BoxPR_curve.png").getFileName().toString());
        assertThrows(IllegalArgumentException.class, () -> service.resolve(directory, "../BoxPR_curve.png"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(directory, "unknown.png"));
    }
}
