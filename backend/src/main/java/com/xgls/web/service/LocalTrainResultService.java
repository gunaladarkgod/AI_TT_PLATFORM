package com.xgls.web.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** 本地目录是运行结果的事实来源；数据库旧结果仅用于兼容和补全历史信息。 */
@Service
public class LocalTrainResultService {
    private final TrainResultArtifactService artifacts;
    private static final Pattern RUN = Pattern.compile("(.+)_from_pyserver_sync_(\\d{8}_\\d{6})_\\d+");

    public LocalTrainResultService(TrainResultArtifactService artifacts) { this.artifacts = artifacts; }
    Path root() { return WorkspacePathUtil.workspaceRoot().resolve("artifacts").toAbsolutePath().normalize(); }

    public synchronized List<TrainResult> list(List<TrainTask> tasks, List<TrainResult> legacy) {
        synchronized (artifacts) { return scan(tasks, legacy); }
    }

    private List<TrainResult> scan(List<TrainTask> tasks, List<TrainResult> legacy) {
        Map<Integer, TrainResult> records = new LinkedHashMap<>();
        for (TrainResult result : legacy) records.put(result.getId(), result);
        Map<String, Integer> indexed = new HashMap<>();
        int nextId = -1;
        Path metadata = root().resolve("result_metadata");
        if (Files.isDirectory(metadata)) {
            try (var files = Files.list(metadata)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches("-?\\d+\\.json")).sorted().toList()) {
                    try {
                        JSONObject meta = JSONUtil.parseObj(Files.readString(file));
                        Integer id = meta.getInt("resultId");
                        if (id == null || id == 0) continue;
                        nextId = Math.min(nextId, id - 1);
                        JSONObject saved = meta.getJSONObject("record");
                        if (saved != null) records.put(id, saved.toBean(TrainResult.class));
                        String dir = portable(meta.getStr("resultDir"));
                        if (dir != null) indexed.put(dir, id);
                    } catch (Exception ignored) { /* 单条索引损坏不能隐藏其他运行。 */ }
                }
            } catch (IOException e) { throw new IllegalStateException("读取结果索引失败", e); }
        }
        Path sequence = metadata.resolve("next-local-id");
        try {
            if (Files.isRegularFile(sequence)) nextId = Math.min(nextId, Integer.parseInt(Files.readString(sequence).trim()));
        } catch (IOException | NumberFormatException e) { throw new IllegalStateException("读取本地结果编号失败", e); }
        Map<String, TrainTask> byName = new HashMap<>();
        for (TrainTask task : tasks) byName.put(task.getName(), task);
        for (Path dir : discover()) {
            String relative = root().relativize(dir).toString().replace('\\', '/');
            Integer id = indexed.get(relative);
            if (id == null) id = nextId--;
            TrainResult result = records.getOrDefault(id, new TrainResult());
            result.setId(id);
            Matcher run = RUN.matcher(dir.getFileName().toString());
            if (!run.matches()) continue;
            result.setTaskName(run.group(1));
            TrainTask task = byName.get(result.getTaskName());
            if (task != null && result.getUserName() == null) {
                result.setTaskId(task.getId());
                result.setUserName(task.getUsername());
            }
            String engine = relative.startsWith("yolo_runs/") ? "ultralytics" :
                    relative.startsWith("custom/") ? "custom" : "mmdet";
            result.setModelType(engine.equals("ultralytics") ? "Ultralytics" : engine.equals("custom") ? "自定义" : "mmdet");
            String log = tail(dir.resolve("train.log"), 256 * 1024);
            String status = status(dir);
            if (status.equals("unknown") && (log.contains("Traceback (most recent call last)") || log.contains("PROCESS NOT STARTED") || log.contains("PROCESS START FAILED"))) status = "failed";
            // 历史日志没有可靠退出码，不能仅凭文件存在就声称训练成功。
            result.setRunStatus(status);
            result.setTraining(status.equals("running") || status.equals("starting"));
            try {
                LocalDateTime start = LocalDateTime.parse(run.group(2), DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path clock = Files.isRegularFile(dir.resolve("train.log")) ? dir.resolve("train.log") : dir;
                LocalDateTime last = LocalDateTime.ofInstant(Files.getLastModifiedTime(clock).toInstant(), ZoneId.systemDefault());
                result.setTime(Boolean.TRUE.equals(result.getTraining()) ? start : last);
                result.setDurationSeconds(Math.max(0, Duration.between(start, last).getSeconds()));
            } catch (Exception ignored) { }
            readMetrics(result, log + "\n" + tail(dir.resolve("coco_metrics.txt"), 16384));
            readCsv(result, dir.resolve("results.csv"));
            JSONObject meta = artifacts.readMeta(id);
            meta.set("resultId", id).set("taskId", result.getTaskId()).set("taskName", result.getTaskName());
            meta.set("resultDir", relative).set("engine", engine).set("record", result);
            if (Files.isRegularFile(dir.resolve("config.py"))) {
                meta.set("configPath", relative + "/config.py").set("configStatus", "copied");
            }
            List<Map<String, String>> weights = new ArrayList<>();
            try (var files = Files.walk(dir)) {
                for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> !dir.relativize(p).startsWith("inference"))
                        .filter(p -> p.getFileName().toString().matches(".*\\.(pth|pt|ckpt|onnx)")).sorted().toList()) {
                    weights.add(Map.of("path", root().relativize(file).toString().replace('\\', '/'),
                            "role", file.getFileName().toString().contains("best") ? "best" : "checkpoint"));
                }
            } catch (IOException ignored) { }
            meta.set("weights", weights).set("weightStatus", weights.isEmpty() ? "missing" : "saved");
            artifacts.writeMeta(meta);
            artifacts.mergeMetadata(result);
            records.put(id, result);
        }
        try {
            Files.createDirectories(metadata);
            Files.writeString(sequence, String.valueOf(nextId));
        } catch (IOException e) { throw new IllegalStateException("保存本地结果编号失败", e); }
        // 本地索引对应目录已移除时不再展示；无目录的历史数据库记录仍保留。
        records.values().removeIf(r -> indexed.containsValue(r.getId()) && artifacts.resultDirectory(r.getId()) == null);
        records.values().forEach(artifacts::mergeMetadata);
        return records.values().stream().sorted(Comparator.comparing(TrainResult::getTime,
                Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private String portable(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String value = stored.replace('\\', '/');
        int marker = value.indexOf("/artifacts/");
        if (marker >= 0) value = value.substring(marker + 11);
        Path path = root().resolve(value).normalize();
        return path.startsWith(root()) ? root().relativize(path).toString().replace('\\', '/') : null;
    }

    private List<Path> discover() {
        List<Path> runs = new ArrayList<>();
        for (String engine : List.of("mmdet_runs", "yolo_runs", "custom", "research")) {
            Path base = root().resolve(engine);
            if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                Files.walkFileTree(base, EnumSet.noneOf(FileVisitOption.class), 6, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (RUN.matcher(dir.getFileName().toString()).matches()) {
                            runs.add(dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) { throw new IllegalStateException("扫描训练结果失败：" + base, e); }
        }
        runs.sort(Comparator.naturalOrder());
        return runs;
    }

    public TrainResult get(Integer id) {
        JSONObject saved = artifacts.readMeta(id).getJSONObject("record");
        if (saved == null) return null;
        TrainResult result = saved.toBean(TrainResult.class);
        String dir = artifacts.resultDirectory(id);
        if (dir != null) {
            String status = status(Path.of(dir));
            result.setRunStatus(status);
            result.setTraining(status.equals("running") || status.equals("starting"));
        }
        return result;
    }

    public synchronized void save(TrainResult result, TrainTask task, JSONObject payload) {
        String wanted = portable(payload.getStr("work_dir"));
        if (wanted == null) return;
        for (TrainResult found : list(List.of(task), List.of())) {
            JSONObject meta = artifacts.readMeta(found.getId());
            if (!wanted.equals(meta.getStr("resultDir"))) continue;
            result.setId(found.getId());
            result.setRunStatus(payload.getBool("ok", false) ? "completed" : "failed");
            result.setTraining(false);
            meta.set("record", result);
            artifacts.writeMeta(meta);
            artifacts.createSnapshot(result, task, payload);
            return;
        }
    }

    public Map<String, Object> log(Integer id) {
        String dir = artifacts.resultDirectory(id);
        if (dir == null) throw new IllegalStateException("结果目录不存在");
        Path path = Path.of(dir).resolve("train.log");
        String content = tail(path, 256 * 1024);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("log_path", path.toString()); out.put("content", content);
        try { out.put("modified_time", Files.getLastModifiedTime(path).toString()); }
        catch (IOException e) { out.put("modified_time", ""); }
        out.put("returned_lines", content.lines().count()); out.put("total_lines", content.lines().count());
        return out;
    }

    private static String status(Path dir) {
        try {
            JSONObject state = JSONUtil.parseObj(tail(dir.resolve("run_status.json"), 16384));
            String status = state.getStr("status", "unknown");
            if (status.equals("running")) {
                Long pid = state.getLong("pid");
                if (pid == null || !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) return "unknown";
            }
            if (status.equals("starting") && Files.getLastModifiedTime(dir.resolve("run_status.json")).toMillis()
                    < System.currentTimeMillis() - 300_000) return "unknown";
            return status;
        } catch (Exception ignored) { return "unknown"; }
    }

    static String tail(Path file, int limit) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return "";
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            channel.position(Math.max(0, channel.size() - limit));
            ByteBuffer buffer = ByteBuffer.allocate(limit);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        } catch (IOException e) { return ""; }
    }

    private static void readMetrics(TrainResult r, String text) {
        String[] keys = {"(?:bbox_mAP|mAP)", "(?:bbox_mAP_50|AP50)", "(?:bbox_mAP_75|AP75)", "(?:bbox_mAP_s|APs)", "(?:bbox_mAP_m|APm)", "(?:bbox_mAP_l|APl)"};
        Double[] values = {r.getMap(), r.getAp50(), r.getAp75(), r.getAps(), r.getApm(), r.getApl()};
        for (int i = 0; i < keys.length; i++) {
            Matcher m = Pattern.compile("(?:^|[\\s/])" + keys[i] + "\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(text);
            while (m.find()) values[i] = Math.max(0, Double.parseDouble(m.group(1)));
        }
        r.setMap(values[0]); r.setAp50(values[1]); r.setAp75(values[2]);
        r.setAps(values[3]); r.setApm(values[4]); r.setApl(values[5]);
    }

    private static void readCsv(TrainResult r, Path csv) {
        if (!Files.isRegularFile(csv, LinkOption.NOFOLLOW_LINKS)) return;
        try (var reader = Files.newBufferedReader(csv)) {
            String header = reader.readLine();
            if (header == null) return;
            String[] keys = header.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != keys.length) continue;
                try {
                    Double map = null, ap50 = null;
                    for (int i = 0; i < keys.length; i++) {
                        if (keys[i].trim().equals("metrics/mAP50-95(B)")) map = Double.valueOf(values[i].trim());
                        if (keys[i].trim().equals("metrics/mAP50(B)")) ap50 = Double.valueOf(values[i].trim());
                    }
                    if (map != null && Double.isFinite(map)) r.setMap(map);
                    if (ap50 != null && Double.isFinite(ap50)) r.setAp50(ap50);
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
    }
}
