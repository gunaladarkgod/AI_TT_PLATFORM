package com.xgls.web.utils;

import cn.hutool.json.JSONUtil;
import com.xgls.web.entity.InstanceDataset;
import java.nio.file.*;
import java.util.List;

/** Format metadata plus actual training artifacts; a YAML alone does not prove YOLO compatibility. */
public final class DatasetFormatUtil {
    private DatasetFormatUtil() {}
    public static void inspect(InstanceDataset dataset, Path root) {
        String declared = null;
        if (dataset.getParamSchema() != null && JSONUtil.isTypeJSONObject(dataset.getParamSchema())) {
            declared = JSONUtil.parseObj(dataset.getParamSchema()).getStr("exportFormat");
        }
        boolean coco = validCoco(root.resolve("annotations/train.json"))
                && validCoco(root.resolve("annotations/test.json"));
        Path yaml = root.resolve("data.yaml");
        boolean yolo = Files.isRegularFile(yaml) && validYolo(root.resolve("labels/train"))
                && validYolo(root.resolve("labels/test"));
        String inferred = coco && yolo ? "both" : coco ? "coco" : yolo ? "yolo" : "unknown";
        String format = List.of("coco", "yolo", "both").contains(declared == null ? "" : declared) ? declared : inferred;
        dataset.setExportFormat(format);
        dataset.setCocoAvailable(coco && !"yolo".equals(format));
        dataset.setYoloAvailable(yolo && !"coco".equals(format));
        dataset.setDataYaml(dataset.isYoloAvailable() ? yaml.toAbsolutePath().normalize().toString().replace('\\', '/') : null);
    }
    private static boolean validCoco(Path file) {
        try {
            var json = JSONUtil.parseObj(Files.readString(file));
            return json.getJSONArray("images") != null && !json.getJSONArray("images").isEmpty()
                    && json.getJSONArray("annotations") != null && json.getJSONArray("categories") != null
                    && !json.getJSONArray("categories").isEmpty();
        } catch (Exception e) { return false; }
    }
    private static boolean validYolo(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        Path images = dir.getParent().getParent().resolve("images").resolve(dir.getFileName());
        if (!Files.isDirectory(images)) return false;
        try (var walk = Files.walk(images)) {
            var imageFiles = walk.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase()
                    .matches(".*\\.(jpg|jpeg|png|bmp|tif|tiff)$")).toList();
            if (imageFiles.isEmpty()) return false;
            for (Path image : imageFiles) {
                Path relative = images.relativize(image);
                String name = relative.getFileName().toString().replaceFirst("\\.[^.]+$", ".txt");
                if (!Files.isRegularFile(dir.resolve(relative).resolveSibling(name))) return false;
            }
        } catch (Exception e) { return false; }
        try (var walk = Files.walk(dir)) {
            var files = walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".txt")).toList();
            if (files.isEmpty()) return false;
            for (Path file : files) for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 5 || !parts[0].matches("\\d+")) return false;
                for (int i = 1; i < 5; i++) {
                    double value = Double.parseDouble(parts[i]);
                    if (!Double.isFinite(value) || value < 0 || value > 1) return false;
                }
            }
            return true;
        } catch (Exception e) { return false; }
    }
}
