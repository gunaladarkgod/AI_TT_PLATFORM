package com.xgls.web.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers and safely resolves standard raster diagnostics produced by Ultralytics. */
@Service
public class TrainingResultImageService {
    private static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    private static final int MAX_IMAGES = 60;
    private static final Pattern TRAIN_BATCH = Pattern.compile("train_batch(\\d+)\\.(?:jpg|jpeg|png)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VAL_BATCH = Pattern.compile("val_batch(\\d+)_(labels|pred)\\.(?:jpg|jpeg|png)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOX_CURVE = Pattern.compile("box(f1|p|pr|r)_curve\\.png", Pattern.CASE_INSENSITIVE);

    public record TrainingImage(String name, String label, String category, String role,
                                long bytes, boolean featured, int sortOrder) { }

    public List<TrainingImage> list(Path directory) throws IOException {
        Path base = directory.toRealPath();
        List<TrainingImage> images = new ArrayList<>();
        long latestTrainBatch = -1;
        try (var paths = Files.list(base)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(base)
                        || Files.size(path) > MAX_IMAGE_BYTES) continue;
                TrainingImage image = describe(path);
                if (image == null) continue;
                images.add(image);
                Matcher train = TRAIN_BATCH.matcher(image.name());
                if (train.matches()) latestTrainBatch = Math.max(latestTrainBatch, Long.parseLong(train.group(1)));
            }
        }
        long featuredTrainBatch = latestTrainBatch;
        List<TrainingImage> normalized = images.stream().map(image -> {
            Matcher train = TRAIN_BATCH.matcher(image.name());
            boolean featured = image.featured()
                    || (train.matches() && Long.parseLong(train.group(1)) == featuredTrainBatch);
            return new TrainingImage(image.name(), image.label(), image.category(), image.role(),
                    image.bytes(), featured, image.sortOrder());
        }).sorted(Comparator.comparingInt(TrainingImage::sortOrder).thenComparing(TrainingImage::name))
                .limit(MAX_IMAGES).toList();
        return normalized;
    }

    public Path resolve(Path directory, String name) throws IOException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("图片名称不能为空");
        Path requested = Path.of(name);
        if (requested.isAbsolute() || requested.getNameCount() != 1
                || !requested.getFileName().toString().equals(name)) {
            throw new IllegalArgumentException("图片名称无效");
        }
        Path base = directory.toRealPath();
        Path image = base.resolve(name).normalize();
        if (!image.startsWith(base) || !Files.isRegularFile(image) || Files.isSymbolicLink(image)
                || !image.toRealPath().startsWith(base) || Files.size(image) > MAX_IMAGE_BYTES
                || describe(image) == null) {
            throw new IllegalArgumentException("训练图片不存在或不受支持");
        }
        return image;
    }

    private TrainingImage describe(Path path) throws IOException {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        long bytes = Files.size(path);
        if ("results.png".equals(lower)) {
            return image(name, "训练结果总览", "overview", "overview", bytes, false, 10);
        }
        if ("confusion_matrix_normalized.png".equals(lower)) {
            return image(name, "归一化混淆矩阵", "evaluation", "matrix", bytes, true, 20);
        }
        if ("confusion_matrix.png".equals(lower)) {
            return image(name, "混淆矩阵", "evaluation", "matrix", bytes, false, 21);
        }
        Matcher curve = BOX_CURVE.matcher(lower);
        if (curve.matches()) {
            String metric = curve.group(1).toUpperCase(Locale.ROOT);
            return image(name, "Box " + metric + " 曲线", "evaluation", "curve", bytes,
                    "PR".equals(metric), 30 + switch (metric) {
                        case "PR" -> 0;
                        case "F1" -> 1;
                        case "P" -> 2;
                        default -> 3;
                    });
        }
        if ("labels.jpg".equals(lower) || "labels.jpeg".equals(lower) || "labels.png".equals(lower)) {
            return image(name, "标签类别与目标分布", "data", "labels", bytes, true, 40);
        }
        Matcher validation = VAL_BATCH.matcher(lower);
        if (validation.matches()) {
            int batch = Integer.parseInt(validation.group(1));
            String role = validation.group(2).toLowerCase(Locale.ROOT);
            return image(name, "pred".equals(role) ? "验证集预测 · Batch " + batch : "验证集标签 · Batch " + batch,
                    "validation", role, bytes, batch == 0, 100 + batch * 2 + ("pred".equals(role) ? 1 : 0));
        }
        Matcher train = TRAIN_BATCH.matcher(lower);
        if (train.matches()) {
            long batch = Long.parseLong(train.group(1));
            return image(name, "训练批次 · " + batch, "training", "sample", bytes, false,
                    1000 + (int) Math.min(batch, 1_000_000));
        }
        return null;
    }

    private TrainingImage image(String name, String label, String category, String role,
                                long bytes, boolean featured, int sortOrder) {
        return new TrainingImage(name, label, category, role, bytes, featured, sortOrder);
    }
}
