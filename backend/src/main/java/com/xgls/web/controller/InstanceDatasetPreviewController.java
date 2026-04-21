package com.xgls.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.mapper.InstanceDatasetMapper;
import com.xgls.web.mapper.TaskDatasetMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 最终实例数据集 - 示例预览 + DOTA 标注框
 */
@Slf4j
@RestController
@RequestMapping("/instanceDataset")
public class InstanceDatasetPreviewController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private InstanceDatasetMapper instanceDatasetMapper;
    @Resource
    private TaskDatasetMapper taskDatasetMapper;

    @GetMapping("/{id}/preview")
    public Map<String, Object> preview(
            @PathVariable("id") Long id,
            @RequestParam(value = "perLabel", required = false, defaultValue = "3") Integer perLabel,
            @RequestParam(value = "part", required = false, defaultValue = "train") String part,
            HttpServletRequest request
    ) {
        if (perLabel == null || perLabel <= 0) {
            perLabel = 3;
        }

        InstanceDataset ds = instanceDatasetMapper.selectById(id);
        if (ds == null) {
            return buildError("实例数据集不存在，id=" + id);
        }

        String imageDir;
        String annoDir;
        if ("test".equalsIgnoreCase(part)) {
            imageDir = ds.getTestImagePath();
            annoDir = ds.getTestAnnoPath();
        } else {
            imageDir = ds.getTrainImagePath();
            annoDir = ds.getTrainAnnoPath();
        }

        String classListStr = ds.getClassList();
        if (!StringUtils.hasText(imageDir) || !StringUtils.hasText(annoDir)) {
            return buildError("实例数据集缺少图片或标注路径");
        }

        Map<String, Integer> classMap = parseClassList(classListStr);
        Map<String, List<String>> label2Images = samplePerLabel(
                Paths.get(imageDir),
                Paths.get(annoDir),
                classMap.keySet(),
                ds.getFatherName(),
                perLabel
        );

        String base = getBaseUrl(request) + "/instanceDataset/" + id + "/image?part=" + part + "&img=";
        List<InstancePreviewItem> items = new ArrayList<>();
        label2Images.forEach((label, fileNames) -> {
            List<String> urls = new ArrayList<>();
            for (String fn : fileNames) {
                String url = base + URLEncoder.encode(fn, StandardCharsets.UTF_8);
                urls.add(url);
            }
            InstancePreviewItem it = new InstancePreviewItem();
            it.setLabel(label);
            it.setImages(urls);
            it.setCount(urls.size());
            items.add(it);
        });

        InstancePreviewResp data = new InstancePreviewResp();
        data.setInstanceDatasetId(id);
        data.setPerLabel(perLabel);
        data.setItems(items);
        return buildOk(data);
    }

    @GetMapping("/{id}/image")
    public void image(
            @PathVariable("id") Long id,
            @RequestParam("img") String img,
            @RequestParam(value = "part", required = false, defaultValue = "train") String part,
            HttpServletResponse response
    ) throws IOException {
        InstanceDataset ds = instanceDatasetMapper.selectById(id);
        if (ds == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String imageDir = "test".equalsIgnoreCase(part) ? ds.getTestImagePath() : ds.getTrainImagePath();
        if (!StringUtils.hasText(imageDir)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Path dir = Paths.get(imageDir);
        Path imgPath = dir.resolve(img);
        if (!Files.exists(imgPath)) {
            imgPath = tryFindImageFile(dir, baseName(img));
        }
        if (imgPath == null || !Files.exists(imgPath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contentType = Files.probeContentType(imgPath);
        if (!StringUtils.hasText(contentType)) {
            contentType = "image/jpeg";
        }
        response.setContentType(contentType);
        try (OutputStream os = response.getOutputStream()) {
            Files.copy(imgPath, os);
        }
    }

    @GetMapping("/{id}/objects")
    public Map<String, Object> objects(
            @PathVariable("id") Long id,
            @RequestParam("img") String img,
            @RequestParam(value = "part", required = false, defaultValue = "train") String part
    ) {
        InstanceDataset ds = instanceDatasetMapper.selectById(id);
        if (ds == null) {
            return buildError("实例数据集不存在，id=" + id);
        }

        String imageDir = "test".equalsIgnoreCase(part) ? ds.getTestImagePath() : ds.getTrainImagePath();
        String annoDir = "test".equalsIgnoreCase(part) ? ds.getTestAnnoPath() : ds.getTrainAnnoPath();
        if (!StringUtils.hasText(imageDir) || !StringUtils.hasText(annoDir)) {
            return buildError("实例数据集缺少图片或标注路径");
        }

        Path imgDir = Paths.get(imageDir);
        Path annoDirPath = Paths.get(annoDir);
        Path imgPath = imgDir.resolve(img);
        if (!Files.exists(imgPath)) {
            try {
                imgPath = tryFindImageFile(imgDir, baseName(img));
            } catch (IOException e) {
                return buildError("查找图片失败: " + img);
            }
        }
        if (imgPath == null || !Files.exists(imgPath)) {
            return buildError("图片不存在: " + img);
        }

        String base = baseName(imgPath.getFileName().toString());
        Path txtPath = annoDirPath.resolve(base + ".txt");
        if (!Files.exists(txtPath)) {
            return buildError("标注文件不存在: " + txtPath);
        }

        int width = 0;
        int height = 0;
        try {
            BufferedImage bi = ImageIO.read(imgPath.toFile());
            if (bi != null) {
                width = bi.getWidth();
                height = bi.getHeight();
            }
        } catch (Exception e) {
            log.warn("读取图片尺寸失败: {}", imgPath, e);
        }

        List<DotaObject> objects = parseDotaFile(txtPath);
        DotaImageObjects dto = new DotaImageObjects();
        dto.setWidth(width);
        dto.setHeight(height);
        dto.setObjects(objects);
        return buildOk(dto);
    }

    private Map<String, Object> buildOk(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "请求成功");
        m.put("success", true);
        m.put("data", data);
        return m;
    }

    private Map<String, Object> buildError(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 500);
        m.put("msg", msg);
        m.put("success", false);
        m.put("data", null);
        return m;
    }

    private String getBaseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    private String baseName(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private Path tryFindImageFile(Path dir, String baseName) throws IOException {
        if (!Files.isDirectory(dir)) return null;
        String[] exts = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};
        for (String ext : exts) {
            Path p = dir.resolve(baseName + ext);
            if (Files.exists(p)) return p;
            Path upper = dir.resolve(baseName + ext.toUpperCase(Locale.ROOT));
            if (Files.exists(upper)) return upper;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (baseName(n).equals(baseName)) {
                    return p;
                }
            }
        }
        return null;
    }

    private Map<String, Integer> parseClassList(String classListStr) {
        Map<String, Integer> ret = new LinkedHashMap<>();
        if (!StringUtils.hasText(classListStr)) return ret;
        try {
            JsonNode node = OBJECT_MAPPER.readTree(classListStr);
            if (node.isObject()) {
                node.fields().forEachRemaining(e -> ret.put(e.getKey(), e.getValue().asInt(0)));
            } else if (node.isArray()) {
                for (JsonNode n : node) {
                    ret.put(n.asText(), 0);
                }
            }
        } catch (Exception e) {
            log.warn("class_list 解析失败: {}", classListStr, e);
        }
        return ret;
    }

    private Map<String, List<String>> samplePerLabel(Path imageDir, Path annoDir, Set<String> labels, String taskName, int perLabel) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String label : labels) {
            result.put(label, new ArrayList<>());
        }
        if (!Files.isDirectory(imageDir) || !Files.isDirectory(annoDir)) {
            return result;
        }

        Map<String, List<String>> candidates = new LinkedHashMap<>();
        for (String label : labels) {
            candidates.put(label, new ArrayList<>());
        }
        Map<String, String> sourceLabelToTarget = resolveSourceLabelToTargetMap(taskName, labels);
        try {
            collectFromDotaTxt(imageDir, annoDir, labels, sourceLabelToTarget, candidates);
            collectFromCocoJson(imageDir, annoDir, labels, sourceLabelToTarget, candidates);
        } catch (Exception e) {
            log.warn("采样预览失败: imageDir={}, annoDir={}", imageDir, annoDir, e);
        }

        if (labels.isEmpty()) {
            for (String label : candidates.keySet()) {
                result.putIfAbsent(label, new ArrayList<>());
            }
        }
        Random random = new Random();
        for (String label : result.keySet()) {
            List<String> files = candidates.getOrDefault(label, new ArrayList<>());
            Collections.shuffle(files, random);
            result.put(label, files.size() > perLabel ? new ArrayList<>(files.subList(0, perLabel)) : files);
        }
        return result;
    }

    private void collectFromDotaTxt(
            Path imageDir,
            Path annoDir,
            Set<String> labels,
            Map<String, String> sourceLabelToTarget,
            Map<String, List<String>> candidates) throws IOException {
        try (var walk = Files.walk(annoDir)) {
            for (Path txt : walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).toList()) {
                List<DotaObject> objects = parseDotaFile(txt);
                Set<String> matched = new LinkedHashSet<>();
                for (DotaObject obj : objects) {
                    String mapped = resolveRequestedLabel(labels, obj.getName(), sourceLabelToTarget);
                    if (mapped != null) {
                        matched.add(mapped);
                    }
                }
                if (matched.isEmpty()) continue;
                String base = baseName(txt.getFileName().toString());
                Path img = tryFindImageFile(imageDir, base);
                if (img == null) continue;
                String rel = imageDir.relativize(img).toString().replace("\\", "/");
                for (String label : matched) {
                    addCandidate(candidates, label, rel);
                }
            }
        }
    }

    private void collectFromCocoJson(
            Path imageDir,
            Path annoDir,
            Set<String> labels,
            Map<String, String> sourceLabelToTarget,
            Map<String, List<String>> candidates) throws IOException {
        try (var walk = Files.walk(annoDir)) {
            for (Path json : walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                JsonNode root;
                try {
                    root = OBJECT_MAPPER.readTree(Files.readString(json, StandardCharsets.UTF_8));
                } catch (Exception ignore) {
                    continue;
                }
                JsonNode images = root.path("images");
                JsonNode annotations = root.path("annotations");
                JsonNode categories = root.path("categories");
                if (!images.isArray() || !annotations.isArray() || !categories.isArray()) {
                    continue;
                }
                Map<Long, String> imageById = new HashMap<>();
                for (JsonNode i : images) {
                    long id = i.path("id").asLong(Long.MIN_VALUE);
                    String fileName = i.path("file_name").asText("");
                    if (id == Long.MIN_VALUE || !StringUtils.hasText(fileName)) continue;
                    imageById.put(id, fileName);
                }
                Map<Long, String> catById = new HashMap<>();
                for (JsonNode c : categories) {
                    long id = c.path("id").asLong(Long.MIN_VALUE);
                    String name = c.path("name").asText("");
                    if (id == Long.MIN_VALUE || !StringUtils.hasText(name)) continue;
                    catById.put(id, name);
                }
                for (JsonNode a : annotations) {
                    long imageId = a.path("image_id").asLong(Long.MIN_VALUE);
                    long catId = a.path("category_id").asLong(Long.MIN_VALUE);
                    if (imageId == Long.MIN_VALUE || catId == Long.MIN_VALUE) continue;
                    String rawLabel = catById.get(catId);
                    String label = resolveRequestedLabel(labels, rawLabel, sourceLabelToTarget);
                    if (label == null) continue;
                    String fileName = imageById.get(imageId);
                    String resolvedName = resolveImageNameForPreview(imageDir, fileName);
                    if (!StringUtils.hasText(resolvedName)) continue;
                    addCandidate(candidates, label, resolvedName);
                }
            }
        }
    }

    private String resolveImageNameForPreview(Path imageDir, String fileName) throws IOException {
        if (!StringUtils.hasText(fileName)) return null;
        Path direct = imageDir.resolve(fileName).normalize();
        if (Files.isRegularFile(direct)) {
            return imageDir.relativize(direct).toString().replace("\\", "/");
        }
        Path byBase = tryFindImageFile(imageDir, baseName(Paths.get(fileName).getFileName().toString()));
        if (byBase == null || !Files.isRegularFile(byBase)) return null;
        return imageDir.relativize(byBase).toString().replace("\\", "/");
    }

    private String resolveRequestedLabel(Set<String> labels, String rawLabel, Map<String, String> sourceLabelToTarget) {
        if (!StringUtils.hasText(rawLabel)) return null;
        String directMapped = sourceLabelToTarget.get(normalizeLabel(rawLabel));
        if (StringUtils.hasText(directMapped)) {
            return directMapped;
        }
        if (labels == null || labels.isEmpty()) return rawLabel;
        String normRaw = normalizeLabel(rawLabel);
        for (String one : labels) {
            if (normalizeLabel(one).equals(normRaw)) {
                return one;
            }
        }
        return null;
    }

    private String normalizeLabel(String s) {
        if (!StringUtils.hasText(s)) return "";
        return s.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private void addCandidate(Map<String, List<String>> candidates, String label, String imgName) {
        List<String> list = candidates.computeIfAbsent(label, k -> new ArrayList<>());
        if (!list.contains(imgName)) {
            list.add(imgName);
        }
    }

    private Map<String, String> resolveSourceLabelToTargetMap(String taskName, Set<String> targetLabels) {
        Map<String, String> map = new HashMap<>();
        if (!StringUtils.hasText(taskName) || targetLabels == null || targetLabels.isEmpty()) {
            return map;
        }
        try {
            TaskDataset task = taskDatasetMapper.selectList(null).stream()
                    .filter(t -> t != null && StringUtils.hasText(t.getName()) && t.getName().equals(taskName))
                    .max(Comparator.comparing(TaskDataset::getId))
                    .orElse(null);
            if (task == null || !StringUtils.hasText(task.getMappingRules())) {
                return map;
            }
            JsonNode root = OBJECT_MAPPER.readTree(task.getMappingRules());
            if (!root.isObject()) return map;
            root.fields().forEachRemaining(dsEntry -> {
                JsonNode oneDataset = dsEntry.getValue();
                if (!oneDataset.isObject()) return;
                oneDataset.fields().forEachRemaining(rule -> {
                    String src = rule.getKey();
                    String dst = rule.getValue() == null ? "" : rule.getValue().asText("");
                    if (!StringUtils.hasText(src) || !StringUtils.hasText(dst)) return;
                    String resolvedDst = null;
                    for (String target : targetLabels) {
                        if (normalizeLabel(target).equals(normalizeLabel(dst))) {
                            resolvedDst = target;
                            break;
                        }
                    }
                    if (resolvedDst != null) {
                        map.put(normalizeLabel(src), resolvedDst);
                    }
                });
            });
        } catch (Exception e) {
            log.warn("解析 mapping_rules 失败", e);
        }
        return map;
    }

    private List<DotaObject> parseDotaFile(Path txtPath) {
        List<DotaObject> list = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(txtPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String s = line == null ? "" : line.trim();
                if (s.isEmpty()) continue;
                String[] parts = s.split("\\s+");
                if (parts.length < 9) continue;
                DotaObject obj = new DotaObject();
                obj.setX1(parseDouble(parts[0]));
                obj.setY1(parseDouble(parts[1]));
                obj.setX2(parseDouble(parts[2]));
                obj.setY2(parseDouble(parts[3]));
                obj.setX3(parseDouble(parts[4]));
                obj.setY3(parseDouble(parts[5]));
                obj.setX4(parseDouble(parts[6]));
                obj.setY4(parseDouble(parts[7]));
                obj.setName(parts[8]);
                list.add(obj);
            }
        } catch (Exception e) {
            log.warn("解析 DOTA 标注失败: {}", txtPath, e);
        }
        return list;
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0d;
        }
    }

    @Data
    public static class InstancePreviewResp {
        private Long instanceDatasetId;
        private Integer perLabel;
        private List<InstancePreviewItem> items;
    }

    @Data
    public static class InstancePreviewItem {
        private String label;
        private Integer count;
        private List<String> images;
    }

    @Data
    public static class DotaImageObjects {
        private int width;
        private int height;
        private List<DotaObject> objects;
    }

    @Data
    public static class DotaObject {
        private double x1;
        private double y1;
        private double x2;
        private double y2;
        private double x3;
        private double y3;
        private double x4;
        private double y4;
        private String name;
    }
}
