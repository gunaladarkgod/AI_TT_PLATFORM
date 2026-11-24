package com.xgls.web.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xgls.web.base.CodeMap;
import com.xgls.web.utils.WorkspacePathUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Local uploads are invisible to component discovery until their training configuration is saved. */
@Slf4j
@Service
public class CustomAlgorithmService {
    private static final long MAX_ZIP_BYTES = 500L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 20_000;
    private static final Duration UPLOAD_LIFETIME = Duration.ofMinutes(30);
    private static final Pattern COMPONENT_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern LOCAL_ID = Pattern.compile("custom/([a-z0-9-]+)/([a-z0-9-]+)");
    private static final Pattern ENGINE_VERSION = Pattern.compile("(?m)^__version__\\s*=\\s*['\"]([0-9][a-zA-Z0-9.+-]*)['\"]");
    private static final Set<String> DIRECTIONS = Set.of("small-object", "knowledge-distillation", "spatiotemporal");
    private static final Set<String> IGNORED = Set.of(".git", "__pycache__", "metadata.properties", "training.json");
    static final String KIND = "custom";

    private final Path root;
    private final Map<String, PendingUpload> pending = new HashMap<>();

    @Autowired
    private TrainTaskService trainingTasks;

    private record PendingUpload(Long owner, Path staging, Properties metadata, Instant expiresAt) {}
    private record PackageContents(Path source, List<String> models, List<String> weights, String engineVersion) {}

    @Autowired
    public CustomAlgorithmService() {
        this(WorkspacePathUtil.workspaceRoot());
    }

    CustomAlgorithmService(Path workspaceRoot) {
        root = workspaceRoot.toAbsolutePath().normalize();
    }

    public synchronized List<Map<String, Object>> list(String direction) {
        if (direction != null && !direction.isBlank()) validateDirection(direction);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : DIRECTIONS) {
            if (direction != null && !direction.isBlank() && !key.equals(direction)) continue;
            Path directory = storageRoot().resolve(key);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : paths.sorted().toList()) {
                    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (Files.isRegularFile(path.resolve("metadata.properties"), LinkOption.NOFOLLOW_LINKS)) {
                        try {
                            result.add(readMetadata(path));
                        } catch (IOException | IllegalArgumentException e) {
                            log.warn("Cannot read local algorithm metadata in {}", path.getFileName(), e);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Cannot read local algorithms in {}", key, e);
            }
        }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("name"))));
        return result;
    }

    public synchronized Map<String, Object> upload(Long owner, String direction, String baselineId, String name, MultipartFile file) {
        validateDirection(direction);
        String selectedBaseline = baselineId == null || baselineId.isBlank() ? "待登记" : baselineId;
        if (!"待登记".equals(selectedBaseline) && (!COMPONENT_ID.matcher(selectedBaseline).matches()
                || !selectedBaseline.startsWith(direction + "/"))) {
            throw new IllegalArgumentException("请选择该研究方向下的基线");
        }
        String cleanName = name == null ? "" : name.trim().replaceAll("[\\r\\n\\t]", " ");
        if (cleanName.isBlank() || cleanName.length() > 80 || cleanName.contains("/") || cleanName.contains("\\")) {
            throw new IllegalArgumentException("算法名称无效");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_ZIP_BYTES
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("请选择不超过 500 MB 的 ZIP 算法包");
        }

        pruneExpiredUploads();
        if (pending.values().stream().anyMatch(upload -> Objects.equals(upload.owner(), owner))) {
            throw new IllegalArgumentException("请先完成或取消当前待确认的算法包");
        }
        String token = UUID.randomUUID().toString();
        Path stagingRoot = storageRoot().resolve(".staging");
        Path staging = stagingRoot.resolve(token);
        Path archive = stagingRoot.resolve(token + ".zip");
        try {
            Files.createDirectories(staging);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, archive);
            }
            Path files = Files.createDirectory(staging.resolve("source"));
            extract(archive, files);
            PackageContents contents = inspect(staging);
            Properties metadata = new Properties();
            String slug = cleanName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (slug.isBlank()) slug = "algorithm";
            metadata.setProperty("id", "custom/" + direction + "/" + slug + "-" + token.substring(0, 8));
            metadata.setProperty("name", cleanName);
            metadata.setProperty("direction", direction);
            metadata.setProperty("baselineId", selectedBaseline);
            metadata.setProperty("repository", "本地上传");
            metadata.setProperty("registrationStatus", "开发中");
            metadata.setProperty("uploadedAt", Instant.now().toString());

            PendingUpload upload = new PendingUpload(owner, staging, metadata, Instant.now().plus(UPLOAD_LIFETIME));
            if (contents.models().size() == 1 && contents.weights().isEmpty()) {
                return publish(upload, contents, contents.models().get(0), "");
            }
            Map<String, Object> choices = choices(contents);
            choices.put("uploadToken", token);
            pending.put(token, upload);
            return choices;
        } catch (ZipException e) {
            deleteQuietly(staging);
            throw new IllegalArgumentException("ZIP 文件损坏或使用了不支持的压缩方式");
        } catch (IOException e) {
            deleteQuietly(staging);
            log.warn("Cannot stage local algorithm package", e);
            throw new IllegalArgumentException("无法保存或解压算法包，请检查可用空间和目录写权限");
        } catch (RuntimeException e) {
            deleteQuietly(staging);
            throw e;
        } finally {
            deleteQuietly(archive);
        }
    }

    public synchronized Map<String, Object> complete(Long owner, String token, String model, String weight) {
        PendingUpload upload = ownedUpload(owner, token);
        pending.remove(token);
        try {
            return publish(upload, inspect(upload.staging()), model, weight);
        } catch (IOException e) {
            log.warn("Cannot publish local algorithm package", e);
            throw new IllegalArgumentException("无法保存算法配置，请检查目录写权限");
        } finally {
            deleteQuietly(upload.staging());
        }
    }

    public synchronized void cancel(Long owner, String token) {
        PendingUpload upload = pending.get(token);
        if (upload != null && Objects.equals(upload.owner(), owner)) {
            pending.remove(token);
            deleteQuietly(upload.staging());
        }
    }

    public synchronized Map<String, Object> inspectExisting(String id) {
        try {
            Path directory = existingPath(id);
            Map<String, Object> result = choices(inspect(directory));
            Path configPath = directory.resolve("training.json");
            if (Files.isRegularFile(configPath, LinkOption.NOFOLLOW_LINKS)) {
                JSONObject saved = JSONUtil.parseObj(Files.readString(configPath));
                result.put("model", saved.getStr("model"));
                result.put("pretrained", saved.getStr("pretrained"));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取本地算法源码");
        }
    }

    public synchronized Map<String, Object> configureExisting(String id, String model, String weight) {
        Path directory = existingPath(id);
        assertNotInUse(id);
        try {
            saveTraining(directory, inspect(directory), model, weight);
            return readMetadata(directory);
        } catch (IOException e) {
            log.warn("Cannot configure local algorithm {}", id, e);
            throw new IllegalArgumentException("无法保存算法配置，请检查目录写权限");
        }
    }

    public synchronized void remove(String id) {
        Path target = existingPath(id);
        assertNotInUse(id);
        deleteQuietly(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("无法完整移除本地算法，请检查目录权限或文件占用");
        }
    }

    public Path localPath(String id) {
        return existingPath(id);
    }

    JSONObject trainingOption(Map<String, Object> metadata, JSONObject template, byte[] platformConfig) {
        JSONObject option = JSONUtil.parseObj(metadata);
        option.set("kind", KIND);
        option.set("engine", "ultralytics");
        option.set("data_format", "yolo");
        option.set("parameters", template.getJSONArray("parameters"));
        option.set("valid", false);
        if (!Boolean.TRUE.equals(metadata.get("trainingReady"))) return option;
        try {
            Path directory = existingPath(option.getStr("id"));
            byte[] configBytes = Files.readAllBytes(directory.resolve("training.json"));
            JSONObject config = JSONUtil.parseObj(new String(configBytes, StandardCharsets.UTF_8));
            option.set("component_commit", config.getStr("source_digest"));
            MessageDigest adapterDigest = digest();
            adapterDigest.update(platformConfig);
            option.set("adapter_digest", HexFormat.of().formatHex(adapterDigest.digest(configBytes)));
            option.set("engine_version", config.getStr("engine_version"));
            option.set("model", config.getStr("model"));
            option.set("pretrained", config.getStr("pretrained"));
            option.set("valid", true);
        } catch (IOException | RuntimeException e) {
            option.set("blockers", List.of("本地训练配置无法读取"));
        }
        return option;
    }

    private Map<String, Object> publish(PendingUpload upload, PackageContents contents, String model, String weight) throws IOException {
        Path staging = upload.staging();
        saveTraining(staging, contents, model, weight);
        try (var output = Files.newOutputStream(staging.resolve("metadata.properties"), StandardOpenOption.CREATE_NEW)) {
            upload.metadata().store(output, "AI_TT_PLATFORM custom algorithm");
        }
        Path target = pathFor(upload.metadata().getProperty("id"));
        Files.createDirectories(target.getParent());
        Map<String, Object> result = readMetadata(staging);
        result.put("path", relative(root, target));
        move(staging, target, false);
        return result;
    }

    private PackageContents inspect(Path directory) throws IOException {
        Path files = Files.isDirectory(directory.resolve("source"), LinkOption.NOFOLLOW_LINKS) ? directory.resolve("source") : directory;
        List<Path> candidates = new ArrayList<>();
        if (hasUltralytics(files)) candidates.add(files);
        try (Stream<Path> children = Files.list(files)) {
            for (Path child : children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (hasUltralytics(child)) candidates.add(child);
            }
        }
        if (candidates.size() != 1) throw new IllegalArgumentException("算法包须包含一份完整的 Ultralytics 源码，支持 ZIP 根目录或一层外目录");
        Path source = candidates.get(0);
        List<String> models = new ArrayList<>();
        List<String> weights = new ArrayList<>();
        for (Path path : sourceFiles(source)) {
            String relative = relative(source, path);
            String lower = relative.toLowerCase(Locale.ROOT);
            if ((lower.endsWith(".yaml") || lower.endsWith(".yml"))
                    && (!relative.contains("/") || lower.startsWith("ultralytics/cfg/models/"))
                    && isModelYaml(path)) models.add(relative);
            if (lower.endsWith(".pt") && (!relative.contains("/") || lower.startsWith("weights/"))) weights.add(relative);
        }
        if (models.isEmpty()) throw new IllegalArgumentException("算法包缺少可用于训练的模型 YAML");
        var version = ENGINE_VERSION.matcher(Files.readString(source.resolve("ultralytics/__init__.py")));
        if (!version.find()) throw new IllegalArgumentException("无法识别算法包中的 Ultralytics 版本");
        return new PackageContents(source, models, weights, version.group(1));
    }

    private static boolean isModelYaml(Path path) throws IOException {
        if (Files.size(path) > 512 * 1024) return false;
        try (InputStream input = Files.newInputStream(path)) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object parsed = new Yaml(new SafeConstructor(options)).load(input);
            return parsed instanceof Map<?, ?> mapping
                    && mapping.get("backbone") instanceof List<?> backbone && !backbone.isEmpty()
                    && mapping.get("head") instanceof List<?> head && !head.isEmpty();
        } catch (YAMLException e) {
            return false;
        }
    }

    private static boolean hasUltralytics(Path directory) {
        return Files.isRegularFile(directory.resolve("ultralytics/__init__.py"), LinkOption.NOFOLLOW_LINKS);
    }

    private void saveTraining(Path directory, PackageContents contents, String model, String weight) throws IOException {
        if (!contents.models().contains(model)) throw new IllegalArgumentException("请选择算法包内有效的模型 YAML");
        if (weight != null && !weight.isBlank() && !contents.weights().contains(weight)) {
            throw new IllegalArgumentException("请选择算法包内有效的初始权重");
        }
        String sourcePath = relative(directory, contents.source());
        JSONObject config = JSONUtil.createObj()
                .set("source", sourcePath.isEmpty() ? "." : sourcePath)
                .set("model", model)
                .set("pretrained", weight == null ? "" : weight)
                .set("engine_version", contents.engineVersion())
                .set("source_digest", sourceDigest(contents.source()));
        Path temporary = Files.createTempFile(directory, ".training-", ".tmp");
        try {
            Files.writeString(temporary, config.toString());
            move(temporary, directory.resolve("training.json"), true);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path source, Path destination, boolean replace) throws IOException {
        try {
            if (replace) Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            if (replace) Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(source, destination);
        }
    }

    private static List<Path> sourceFiles(Path source) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, java.nio.file.attribute.BasicFileAttributes attributes) {
                return !directory.equals(source) && IGNORED.contains(directory.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (IGNORED.contains(file.getFileName().toString())) return FileVisitResult.CONTINUE;
                if (!attributes.isRegularFile()) throw new IOException("Unsupported file type in algorithm package");
                result.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.comparing(path -> relative(source, path)));
        return result;
    }

    private static String sourceDigest(Path source) throws IOException {
        MessageDigest aggregate = digest();
        byte[] buffer = new byte[65536];
        for (Path path : sourceFiles(source)) {
            MessageDigest file = digest();
            try (InputStream input = Files.newInputStream(path)) {
                int count;
                while ((count = input.read(buffer)) != -1) file.update(buffer, 0, count);
            }
            aggregate.update(relative(source, path).getBytes(StandardCharsets.UTF_8));
            aggregate.update((byte) 0);
            aggregate.update(file.digest());
        }
        return HexFormat.of().formatHex(aggregate.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> choices(PackageContents contents) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", contents.models());
        result.put("weights", contents.weights());
        return result;
    }

    private void extract(Path archive, Path staging) throws IOException {
        long extracted = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            if (zip.size() > MAX_ENTRIES) throw new IllegalArgumentException("算法包文件数量过多");
            byte[] buffer = new byte[8192];
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                String[] parts = name.split("/");
                if (name.isBlank() || name.startsWith("/") || name.contains(":") || Arrays.asList(parts).contains("..")) {
                    throw new IllegalArgumentException("算法包包含非法路径");
                }
                if (Arrays.stream(parts).anyMatch(IGNORED::contains)) continue;
                Path destination = staging.resolve(name).normalize();
                if (!destination.startsWith(staging) || destination.equals(staging)) throw new IllegalArgumentException("算法包包含非法路径");
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                var crc = new java.util.zip.CRC32();
                long size = 0;
                try (var input = zip.getInputStream(entry);
                     var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count;
                        extracted += count;
                        if (extracted > MAX_EXTRACTED_BYTES) throw new IllegalArgumentException("算法包解压后过大");
                        crc.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                }
                if (size != entry.getSize() || crc.getValue() != entry.getCrc()) throw new ZipException("Invalid entry checksum");
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("算法包包含非法路径");
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("算法包包含重复或冲突的文件路径");
        }
    }

    private Map<String, Object> readMetadata(Path directory) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(directory.resolve("metadata.properties"))) {
            properties.load(input);
        }
        boolean ready = Files.isRegularFile(directory.resolve("training.json"), LinkOption.NOFOLLOW_LINKS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", properties.getProperty("id", ""));
        result.put("name", properties.getProperty("name", "未命名算法"));
        result.put("direction", properties.getProperty("direction", ""));
        result.put("baselineId", properties.getProperty("baselineId", ""));
        result.put("repository", "本地上传");
        result.put("registrationStatus", "开发中");
        result.put("localStatus", "uploaded");
        result.put("trainingReady", ready);
        result.put("path", relative(root, directory));
        result.put("blockers", ready ? List.of() : List.of("尚未选择训练模型配置"));
        result.put("custom", true);
        return result;
    }

    private PendingUpload ownedUpload(Long owner, String token) {
        pruneExpiredUploads();
        PendingUpload upload = pending.get(token);
        if (upload == null || !Objects.equals(upload.owner(), owner)) throw new IllegalArgumentException("上传会话已失效，请重新上传");
        return upload;
    }

    private void pruneExpiredUploads() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isAfter(now)) return false;
            deleteQuietly(entry.getValue().staging());
            return true;
        });
    }

    private Path existingPath(String id) {
        Path target = pathFor(id);
        try {
            if (!Files.isRegularFile(target.resolve("metadata.properties"), LinkOption.NOFOLLOW_LINKS)
                    || !target.toRealPath().startsWith(root.toRealPath().resolve("local-components/custom-algorithms"))
                    || !target.toRealPath().equals(root.toRealPath().resolve(root.relativize(target)))) {
                throw new IllegalArgumentException("本地算法不存在或目录无效");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("本地算法不存在或目录无效");
        }
        return target;
    }

    private Path pathFor(String id) {
        var match = LOCAL_ID.matcher(id == null ? "" : id);
        if (!match.matches() || !DIRECTIONS.contains(match.group(1))) throw new IllegalArgumentException("自定义算法 ID 无效");
        return storageRoot().resolve(match.group(1)).resolve(match.group(2));
    }

    private Path storageRoot() {
        return root.resolve("local-components/custom-algorithms");
    }

    private static String relative(Path base, Path path) {
        return base.relativize(path).toString().replace(FileSystems.getDefault().getSeparator(), "/");
    }

    private static void validateDirection(String direction) {
        if (direction == null || !DIRECTIONS.contains(direction)) throw new IllegalArgumentException("研究方向无效");
    }

    private void assertNotInUse(String id) {
        if (trainingTasks == null) return;
        for (var task : trainingTasks.list()) {
            if (!CodeMap.TRAIN_TASK_STATUS_QUEUE.equals(task.getStatus())
                    && !CodeMap.TRAIN_TASK_STATUS_RUN.equals(task.getStatus())) continue;
            var options = trainingTasks.runnerOptionsForTask(task.getId());
            if (id.equals(options.getStr("component_id")) && KIND.equals(options.getStr("component_kind"))) {
                throw new IllegalArgumentException("算法正被排队或运行任务使用，请先取消或停止任务：" + task.getName());
            }
        }
    }

    private static void deleteQuietly(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        } catch (IOException e) {
            log.warn("Cannot clean up temporary or removed local algorithm files", e);
        }
    }

    @PreDestroy
    public synchronized void close() {
        pending.values().forEach(upload -> deleteQuietly(upload.staging()));
        pending.clear();
    }
}
