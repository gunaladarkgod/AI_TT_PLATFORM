package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Stores user-uploaded algorithm packages outside the repository-owned registry. */
@Service
public class CustomAlgorithmService {
    private static final long MAX_ZIP_BYTES = 500L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private static final Pattern DIRECTION = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern COMPONENT_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> DIRECTIONS = Set.of("small-object", "knowledge-distillation", "spatiotemporal");

    private final Path root;

    @Autowired
    public CustomAlgorithmService() { this(WorkspacePathUtil.workspaceRoot()); }

    CustomAlgorithmService(Path workspaceRoot) {
        this.root = workspaceRoot.toAbsolutePath().normalize();
    }

    public synchronized List<Map<String, Object>> list(String direction) {
        validateDirection(direction, false);
        Path base = storageRoot();
        if (!Files.isDirectory(base)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> directions = Files.list(base)) {
            directions.filter(Files::isDirectory).forEach(path -> {
                if (direction != null && !direction.isBlank() && !direction.equals(path.getFileName().toString())) return;
                try (Stream<Path> algorithms = Files.list(path)) {
                    algorithms.filter(Files::isDirectory).forEach(algorithm -> {
                        Path metadata = algorithm.resolve("metadata.properties");
                        if (Files.isRegularFile(metadata)) result.add(readMetadata(metadata, algorithm));
                    });
                } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("name"))));
        return result;
    }

    public synchronized Map<String, Object> upload(String direction, String baselineId, String name, MultipartFile file) {
        validateDirection(direction, true);
        String selectedBaseline = baselineId == null || baselineId.isBlank() ? "待登记" : baselineId;
        if (!"待登记".equals(selectedBaseline) && !COMPONENT_ID.matcher(selectedBaseline).matches()) throw new IllegalArgumentException("请选择有效的基线");
        String cleanName = cleanName(name);
        if (file == null || file.isEmpty() || file.getSize() > MAX_ZIP_BYTES
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("请选择不超过 500 MB 的 ZIP 算法包");
        }

        Path base = storageRoot().resolve(direction).normalize();
        Path stagingRoot = storageRoot().resolve(".staging").normalize();
        String slug = slug(cleanName) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path target = base.resolve(slug).normalize();
        Path staging = stagingRoot.resolve(UUID.randomUUID().toString()).normalize();
        if (!target.startsWith(base) || !staging.startsWith(stagingRoot)) throw new IllegalArgumentException("算法包路径无效");
        try {
            Files.createDirectories(staging);
            extract(file.getInputStream(), staging);
            Files.createDirectories(base);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target);
            }
            Properties properties = new Properties();
            properties.setProperty("id", "custom/" + direction + "/" + slug);
            properties.setProperty("name", cleanName);
            properties.setProperty("direction", direction);
            properties.setProperty("baselineId", selectedBaseline);
            properties.setProperty("repository", "本地上传");
            properties.setProperty("registrationStatus", "待登记");
            properties.setProperty("uploadedAt", Instant.now().toString());
            try (var output = Files.newOutputStream(target.resolve("metadata.properties"))) {
                properties.store(output, "AI_TT_PLATFORM custom algorithm");
            }
            return readMetadata(target.resolve("metadata.properties"), target);
        } catch (IOException e) {
            deleteQuietly(staging);
            deleteQuietly(target);
            throw new IllegalArgumentException("算法包上传或解压失败");
        } catch (RuntimeException e) {
            deleteQuietly(staging);
            deleteQuietly(target);
            throw e;
        }
    }

    public synchronized void remove(String id) {
        if (id == null || !id.startsWith("custom/")) throw new IllegalArgumentException("自定义算法 ID 无效");
        String[] parts = id.split("/", 3);
        if (parts.length != 3 || !DIRECTIONS.contains(parts[1]) || parts[2].isBlank() || parts[2].contains("..")) throw new IllegalArgumentException("自定义算法 ID 无效");
        Path base = storageRoot().resolve(parts[1]).normalize();
        Path target = base.resolve(parts[2]).normalize();
        if (!target.startsWith(base) || !Files.isDirectory(target)) throw new IllegalArgumentException("自定义算法不存在");
        deleteQuietly(target);
    }

    private void extract(InputStream input, Path staging) throws IOException {
        long extracted = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("算法包文件数量过多");
                String name = entry.getName().replace('\\', '/');
                Path destination = staging.resolve(name).normalize();
                if (name.isBlank() || name.startsWith("/") || !destination.startsWith(staging)) throw new IllegalArgumentException("算法包包含非法路径");
                if (entry.isDirectory()) { Files.createDirectories(destination); continue; }
                Files.createDirectories(destination.getParent());
                try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        extracted += count;
                        if (extracted > MAX_EXTRACTED_BYTES) throw new IllegalArgumentException("算法包解压后过大");
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private Map<String, Object> readMetadata(Path metadata, Path algorithm) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) { properties.load(input); }
        catch (IOException e) { return Map.of("id", "", "name", "未知算法", "localStatus", "invalid"); }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", properties.getProperty("id", ""));
        result.put("name", properties.getProperty("name", "未命名算法"));
        result.put("direction", properties.getProperty("direction", ""));
        result.put("baselineId", properties.getProperty("baselineId", ""));
        result.put("repository", properties.getProperty("repository", "本地上传"));
        result.put("registrationStatus", properties.getProperty("registrationStatus", "待登记"));
        result.put("localStatus", "uploaded");
        result.put("trainingReady", false);
        result.put("path", root.relativize(algorithm).toString().replace(FileSystems.getDefault().getSeparator(), "/"));
        result.put("blockers", List.of("自定义算法尚未完成平台登记"));
        result.put("custom", true);
        return result;
    }

    private Path storageRoot() { return root.resolve("local-components").resolve("custom-algorithms").normalize(); }
    private void validateDirection(String direction, boolean required) {
        if ((direction == null || direction.isBlank()) && !required) return;
        if (direction == null || !DIRECTIONS.contains(direction) || !DIRECTION.matcher(direction).matches()) throw new IllegalArgumentException("研究方向无效");
    }
    private String cleanName(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("[\\r\\n\\t]", " ");
        if (clean.isBlank() || clean.length() > 80 || clean.contains("/") || clean.contains("\\")) throw new IllegalArgumentException("算法名称无效");
        return clean;
    }
    private String slug(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""); }
    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) { paths.sorted(Comparator.reverseOrder()).forEach(item -> { try { Files.deleteIfExists(item); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }

    @PreDestroy public void close() { }
}
