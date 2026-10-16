package com.xgls.web.service;

import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** ZIP import/export for native MMDetection and Ultralytics run directories. */
@Service
public class TrainResultPackageService {
    private static final long MAX_ZIP_BYTES = 500L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;

    private final TrainResultService results;
    private final TrainResultArtifactService artifacts;
    private final Path rootOverride;

    @Autowired
    public TrainResultPackageService(TrainResultService results, TrainResultArtifactService artifacts) {
        this(results, artifacts, null);
    }

    TrainResultPackageService(TrainResultService results, TrainResultArtifactService artifacts, Path rootOverride) {
        this.results = results;
        this.artifacts = artifacts;
        this.rootOverride = rootOverride;
    }

    private Path artifactsRoot() {
        return (rootOverride == null ? WorkspacePathUtil.workspaceRoot().resolve("artifacts") : rootOverride)
                .toAbsolutePath().normalize();
    }

    public TrainResult importPackage(MultipartFile file, String username) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_ZIP_BYTES
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("请选择不超过 500 MB 的 ZIP 结果包");
        }
        Path root = artifactsRoot();
        Path stagingRoot = root.resolve("import_staging");
        Files.createDirectories(stagingRoot);
        Path staging = Files.createTempDirectory(stagingRoot, "result-");
        Path finalDir = null;
        TrainResult result = null;
        try {
            extract(file, staging);
            Path contents = packageRoot(staging);
            boolean yolo = Files.isRegularFile(contents.resolve("results.csv"));
            boolean mmdet;
            try (var paths = Files.walk(contents, 3)) {
                mmdet = paths.anyMatch(path -> Files.isRegularFile(path)
                        && "scalars.json".equals(path.getFileName().toString())
                        && path.getParent() != null
                        && "vis_data".equals(path.getParent().getFileName().toString()));
            }
            if (yolo == mmdet) {
                throw new IllegalArgumentException("结果包须包含 results.csv 或 vis_data/scalars.json，且只能属于一种引擎");
            }
            String engine = yolo ? "ultralytics" : "mmdet";
            if (yolo) validateYolo(contents.resolve("results.csv"));
            else validateMmdet(contents);

            String fileName = Path.of(file.getOriginalFilename().replace('\\', '/')).getFileName().toString();
            String originalName = fileName.substring(0, fileName.length() - 4);
            String displayName = originalName
                    .replaceAll("[^a-zA-Z0-9_-]", "_");
            if (displayName.isBlank()) displayName = "imported_result";
            if (displayName.length() > 60) displayName = displayName.substring(0, 60);
            String runId = displayName + "_" + UUID.randomUUID().toString().substring(0, 8);
            Path importRoot = root.resolve(yolo ? "yolo_runs" : "mmdet_runs").resolve("imported");
            Files.createDirectories(importRoot);
            finalDir = importRoot.resolve(runId + "_from_pyserver_sync_" + System.currentTimeMillis());
            Files.move(contents, finalDir);

            result = new TrainResult();
            result.setTaskName(runId);
            result.setUserName(username);
            result.setModelType(yolo ? "Ultralytics" : "mmdet");
            result.setTime(LocalDateTime.now());
            if (yolo) fillYoloSummary(result, finalDir);
            if (!results.save(result)) throw new IllegalStateException("创建训练结果记录失败");
            artifacts.createImportedSnapshot(result, engine, originalName, finalDir);
            artifacts.mergeMetadata(result);
            return result;
        } catch (IOException | RuntimeException error) {
            if (result != null && result.getId() != null) {
                results.removeById(result.getId());
                artifacts.deleteMetadata(result.getId());
            }
            if (finalDir != null) deleteTree(finalDir);
            throw error;
        } finally {
            deleteTree(staging);
        }
    }

    void extract(MultipartFile file, Path staging) throws IOException {
        long total = 0;
        int entries = 0;
        Set<Path> seen = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("ZIP 文件数量超过限制");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0
                        || List.of(name.split("/")).contains("..")) {
                    throw new IllegalArgumentException("ZIP 包含无效文件路径");
                }
                Path target = staging.resolve(name).normalize();
                if (!target.startsWith(staging) || target.equals(staging)) {
                    throw new IllegalArgumentException("ZIP 包含越界文件路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                if (!seen.add(target)) throw new IllegalArgumentException("ZIP 包含重复文件路径");
                Files.createDirectories(target.getParent());
                try (OutputStream output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_EXTRACTED_BYTES) throw new IllegalArgumentException("ZIP 解压后超过 2 GB 限制");
                        output.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
        if (entries == 0) throw new IllegalArgumentException("ZIP 结果包为空");
    }

    Path packageRoot(Path staging) throws IOException {
        try (var children = Files.list(staging)) {
            List<Path> top = children.toList();
            if (top.size() == 1 && Files.isDirectory(top.get(0))
                    && !"vis_data".equals(top.get(0).getFileName().toString())) return top.get(0);
        }
        return staging;
    }

    void validateYolo(Path csv) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.contains("epoch")
                    || !header.contains("metrics/") || !header.contains("loss")) {
                throw new IllegalArgumentException("results.csv 缺少有效训练指标");
            }
            int epochIndex = -1;
            String[] names = header.split(",");
            for (int i = 0; i < names.length; i++) if (names[i].trim().equals("epoch")) epochIndex = i;
            if (epochIndex < 0) throw new IllegalArgumentException("results.csv 缺少轮次列");
            String row;
            while ((row = reader.readLine()) != null) {
                String[] cells = row.split(",", -1);
                if (epochIndex < cells.length) {
                    try {
                        if (!Double.isFinite(Double.parseDouble(cells[epochIndex].trim()))) continue;
                        for (int i = 0; i < names.length && i < cells.length; i++) {
                            String name = names[i].trim();
                            if (!name.contains("loss") && !name.startsWith("metrics/") && !name.startsWith("lr/")) continue;
                            try {
                                if (Double.isFinite(Double.parseDouble(cells[i].trim()))) return;
                            } catch (NumberFormatException ignored) { }
                        }
                    } catch (NumberFormatException ignored) { }
                }
            }
            throw new IllegalArgumentException("results.csv 没有可绘制的训练指标");
        }
    }

    void validateMmdet(Path root) throws IOException {
        try (var paths = Files.walk(root, 3)) {
            Path scalar = paths.filter(path -> Files.isRegularFile(path)
                    && "scalars.json".equals(path.getFileName().toString())
                    && "vis_data".equals(path.getParent().getFileName().toString()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("缺少 scalars.json"));
            try (BufferedReader reader = Files.newBufferedReader(scalar, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        var record = JSONUtil.parseObj(line);
                        if (!record.containsKey("step") && !record.containsKey("epoch")) continue;
                        for (String key : record.keySet()) {
                            String lower = key.toLowerCase();
                            if ((lower.contains("loss") || lower.contains("map") || lower.equals("acc")
                                    || lower.equals("lr")) && record.get(key) instanceof Number) return;
                        }
                    } catch (RuntimeException ignored) { }
                }
                throw new IllegalArgumentException("scalars.json 没有可绘制的训练指标");
            }
        }
    }

    private void fillYoloSummary(TrainResult result, Path directory) throws IOException {
        Path args = directory.resolve("args.yaml");
        if (Files.isRegularFile(args)) {
            for (String line : Files.readAllLines(args, StandardCharsets.UTF_8)) {
                if (line.startsWith("model:")) result.setNetworkName(line.substring(6).trim().replace("'", ""));
                if (line.startsWith("data:")) result.setDataset(line.substring(5).trim().replace("'", ""));
            }
        }
        try (BufferedReader reader = Files.newBufferedReader(directory.resolve("results.csv"), StandardCharsets.UTF_8)) {
            String[] header = reader.readLine().split(",");
            int mapIndex = -1, ap50Index = -1;
            for (int i = 0; i < header.length; i++) {
                if (header[i].trim().equals("metrics/mAP50-95(B)")) mapIndex = i;
                if (header[i].trim().equals("metrics/mAP50(B)")) ap50Index = i;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                result.setMap(maxMetric(result.getMap(), cells, mapIndex));
                result.setAp50(maxMetric(result.getAp50(), cells, ap50Index));
            }
        }
    }

    private Double maxMetric(Double previous, String[] cells, int index) {
        if (index < 0 || index >= cells.length) return previous;
        try {
            double value = Double.parseDouble(cells[index].trim());
            return Double.isFinite(value) ? (previous == null ? value : Math.max(previous, value)) : previous;
        } catch (NumberFormatException ignored) {
            return previous;
        }
    }

    public void exportPackage(Path directory, OutputStream stream) throws IOException {
        Path root = artifactsRoot();
        Path base = directory.toRealPath();
        if (!base.startsWith(root.toRealPath())) throw new IllegalArgumentException("结果目录不在产物根目录内");
        try (ZipOutputStream zip = new ZipOutputStream(stream, StandardCharsets.UTF_8);
             var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(base)) continue;
                String name = base.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Keep a failed staging area for manual cleanup instead of deleting another directory.
        }
    }
}
