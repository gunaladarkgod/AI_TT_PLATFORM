package com.xgls.web.utils;

import cn.hutool.json.JSONUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts the processed DOTA annotations into files consumed by the training engines. */
public final class DatasetAnnotationExportUtil {
    private DatasetAnnotationExportUtil() {}

    public static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return "both";
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (!List.of("coco", "yolo", "both").contains(normalized)) {
            throw new IllegalArgumentException("不支持的导出标注格式: " + format);
        }
        return normalized;
    }

    public static void export(Path datasetRoot, List<String> classNames, String format) throws IOException {
        String selected = normalizeFormat(format);
        if (classNames == null || classNames.isEmpty()) {
            throw new IllegalArgumentException("实例数据集缺少类别，无法导出标注");
        }
        boolean coco = !"yolo".equals(selected);
        boolean yolo = !"coco".equals(selected);
        Path labelsRoot = datasetRoot.resolve("labels");
        if (yolo && Files.exists(labelsRoot)
                && Files.isSameFile(labelsRoot, datasetRoot.resolve("annotations"))) {
            throw new IOException("labels 仍指向 DOTA annotations，无法写入 YOLO 标注");
        }
        for (String split : List.of("train", "test")) {
            Path imagesDir = datasetRoot.resolve("images").resolve(split);
            Path dotaDir = datasetRoot.resolve("annotations").resolve(split);
            Path labelsDir = datasetRoot.resolve("labels").resolve(split);
            if (yolo) {
                if (Files.isDirectory(labelsDir)) {
                    try (var walk = Files.walk(labelsDir)) {
                        for (Path old : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                            if (!old.equals(labelsDir)) Files.delete(old);
                        }
                    }
                }
                Files.createDirectories(labelsDir);
            }
            List<Map<String, Object>> images = new ArrayList<>();
            List<Map<String, Object>> annotations = new ArrayList<>();
            long imageId = 0;
            long annotationId = 0;
            try (var walk = Files.walk(imagesDir)) {
                for (Path image : walk.filter(Files::isRegularFile).sorted().toList()) {
                    if (!isImage(image)) continue;
                    Path relative = imagesDir.relativize(image);
                    String filename = relative.getFileName().toString();
                    String stem = filename.substring(0, filename.lastIndexOf('.'));
                    Path txt = dotaDir.resolve(relative).resolveSibling(stem + ".txt");
                    if (!Files.isRegularFile(txt)) {
                        throw new IOException("图片缺少 DOTA TXT 标注: " + relative);
                    }
                    BufferedImage bitmap = ImageIO.read(image.toFile());
                    if (bitmap == null) throw new IOException("无法读取图片尺寸: " + image);
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();
                    if (width <= 0 || height <= 0) throw new IOException("图片尺寸无效: " + image);
                    imageId++;
                    images.add(Map.of("id", imageId, "file_name", relative.toString().replace('\\', '/'),
                            "width", width, "height", height));
                    List<String> yoloLines = new ArrayList<>();
                    for (String line : Files.readAllLines(txt, StandardCharsets.UTF_8)) {
                        if (line.isBlank()) continue;
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length < 9) throw new IOException("DOTA 标注列数不足: " + txt);
                        int classId = classNames.indexOf(parts[8]);
                        if (classId < 0) throw new IOException("标注类别不在实例数据集类别列表中: " + parts[8]);
                        double[] points = new double[8];
                        try {
                            for (int i = 0; i < 8; i++) points[i] = Double.parseDouble(parts[i]);
                        } catch (NumberFormatException e) {
                            throw new IOException("DOTA 标注坐标无效: " + txt, e);
                        }
                        double x1 = Math.max(0, Math.min(width, Math.min(Math.min(points[0], points[2]), Math.min(points[4], points[6]))));
                        double x2 = Math.max(0, Math.min(width, Math.max(Math.max(points[0], points[2]), Math.max(points[4], points[6]))));
                        double y1 = Math.max(0, Math.min(height, Math.min(Math.min(points[1], points[3]), Math.min(points[5], points[7]))));
                        double y2 = Math.max(0, Math.min(height, Math.max(Math.max(points[1], points[3]), Math.max(points[5], points[7]))));
                        if (!Double.isFinite(x1 + x2 + y1 + y2) || x2 <= x1 || y2 <= y1) {
                            throw new IOException("DOTA 标注框无效: " + txt);
                        }
                        double boxWidth = x2 - x1;
                        double boxHeight = y2 - y1;
                        annotationId++;
                        Map<String, Object> annotation = new LinkedHashMap<>();
                        annotation.put("id", annotationId);
                        annotation.put("image_id", imageId);
                        annotation.put("category_id", classId + 1);
                        annotation.put("bbox", List.of(x1, y1, boxWidth, boxHeight));
                        annotation.put("area", boxWidth * boxHeight);
                        annotation.put("iscrowd", 0);
                        annotation.put("segmentation", List.of(Arrays.stream(points).boxed().toList()));
                        annotations.add(annotation);
                        yoloLines.add(String.format(Locale.ROOT, "%d %.8f %.8f %.8f %.8f", classId,
                                (x1 + x2) / (2 * width), (y1 + y2) / (2 * height), boxWidth / width, boxHeight / height));
                    }
                    if (yolo) {
                        Path label = labelsDir.resolve(relative).resolveSibling(stem + ".txt");
                        Files.createDirectories(label.getParent());
                        Files.write(label, yoloLines, StandardCharsets.UTF_8);
                    }
                }
            }
            if (coco) {
                List<Map<String, Object>> categories = new ArrayList<>();
                for (int i = 0; i < classNames.size(); i++) {
                    categories.add(Map.of("id", i + 1, "name", classNames.get(i), "supercategory", "none"));
                }
                Path json = datasetRoot.resolve("annotations").resolve(split + ".json");
                Files.writeString(json, JSONUtil.toJsonPrettyStr(Map.of("images", images,
                        "annotations", annotations, "categories", categories)), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return List.of(".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff").stream().anyMatch(name::endsWith);
    }
}
