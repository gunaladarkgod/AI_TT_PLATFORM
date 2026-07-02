package com.xgls.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.mapper.InstanceDatasetMapper;
import com.xgls.web.mapper.TaskDatasetMapper;
import com.xgls.web.utils.WorkspacePathUtil;
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

        Path imageDirPath = resolveInstanceDatasetPath(imageDir);
        Path annoDirPath = resolveInstanceDatasetPath(annoDir);

        Map<String, Integer> classMap = parseClassList(classListStr);
        if (classMap.isEmpty()) {
            classMap = inferClassMap(imageDirPath, annoDirPath);
        }
        Map<String, List<String>> label2Images = samplePerLabel(
                imageDirPath,
                annoDirPath,
                classMap.keySet(),
                ds.getFatherName(),
                perLabel
        );

        String base = getBaseUrl(request) + "/instanceDataset/" + id + "/image?part=" + part + "&img=";
        List<InstancePreviewItem> items = new ArrayList<>();
        label2Images.forEach((label, fileNames) -> {            List<InstancePreviewImage> images = new ArrayList<>();
            Path imgDir = imageDirPath;
            for (String fn : fileNames) {
                String url = base + URLEncoder.encode(fn, StandardCharsets.UTF_8);
                InstancePreviewImage image = new InstancePreviewImage();
                image.setUrl(url);
                image.setFileName(fn);
                try {
                    Path imgPath = resolveImagePath(imgDir, fn);
                    int[] wh = readImageWH(imgPath);
                    image.setWidth(wh[0]);
                    image.setHeight(wh[1]);
                    image.setObjects(readObjectsForImage(imgDir, annoDirPath, imgPath));
                } catch (Exception e) {
                    image.setObjects(Collections.emptyList());
                }
                images.add(image);
            }
            InstancePreviewItem it = new InstancePreviewItem();
            it.setLabel(label);
            it.setImages(images);
            it.setCount(images.size());
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

        Path dir = resolveInstanceDatasetPath(imageDir);
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

        Path imgDir = resolveInstanceDatasetPath(imageDir);
        Path annoDirPath = resolveInstanceDatasetPath(annoDir);
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
        }        int width = 0;
        int height = 0;
        try {
            BufferedImage bi = ImageIO.read(imgPath.toFile());
            if (bi != null) {
                width = bi.getWidth();
                height = bi.getHeight();
            }
        } catch (Exception e) {
            log.warn("read image size failed: {}", imgPath, e);
        }

        List<DotaObject> objects = readObjectsForImage(imgDir, annoDirPath, imgPath);
        DotaImageObjects dto = new DotaImageObjects();
        dto.setWidth(width);
        dto.setHeight(height);
        dto.setObjects(objects);
        return buildOk(dto);
    }

    private Path resolveImagePath(Path imageDir, String img) throws IOException {
        Path imgPath = imageDir.resolve(img).normalize();
        if (Files.exists(imgPath)) return imgPath;
        Path found = tryFindImageFile(imageDir, baseName(Paths.get(img).getFileName().toString()));
        if (found == null) throw new IOException("image not found: " + img);
        return found;
    }

    private int[] readImageWH(Path imgPath) {
        int width = 0;
        int height = 0;
        try {
            BufferedImage bi = ImageIO.read(imgPath.toFile());
            if (bi != null) {
                width = bi.getWidth();
                height = bi.getHeight();
            }
        } catch (Exception ignore) {}
        return new int[]{width, height};
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
        String scheme = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        String host = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        String portH = request.getHeader("X-Forwarded-Port");
        String prefix = Optional.ofNullable(request.getHeader("X-Forwarded-Prefix")).orElse("");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (!("http".equalsIgnoreCase(scheme) && port == 80) && !("https".equalsIgnoreCase(scheme) && port == 443)) {
                host = host + ":" + port;
            }
        } else if (!host.contains(":")) {
            if (portH != null && !portH.isBlank()) {
                if (!("http".equalsIgnoreCase(scheme) && "80".equals(portH)) && !("https".equalsIgnoreCase(scheme) && "443".equals(portH))) {
                    host = host + ":" + portH;
                }
            }
        }
        if (!prefix.isEmpty() && prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        return scheme + "://" + host + prefix;
    }

    private Path resolveInstanceDatasetPath(String rawPath) {
        Path path = Paths.get(rawPath).normalize();
        if (path.isAbsolute()) {
            return path;
        }
        return WorkspacePathUtil.instanceDatasetRoot().resolve(path).normalize();
    }
    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
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

    private Map<String, Integer> inferClassMap(Path imageDir, Path annoDir) {
        Map<String, Integer> ret = new LinkedHashMap<>();
        if (!Files.isDirectory(annoDir)) return ret;
        try (var walk = Files.walk(annoDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".txt")) {
                    for (DotaObject obj : parseDotaFile(file)) {
                        if (StringUtils.hasText(obj.getName())) ret.merge(obj.getName(), 1, Integer::sum);
                    }
                } else if (name.endsWith(".json")) {
                    JsonNode root = OBJECT_MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
                    JsonNode categories = root.path("categories");
                    if (categories.isArray()) {
                        for (JsonNode c : categories) {
                            String label = c.path("name").asText("");
                            if (StringUtils.hasText(label)) ret.putIfAbsent(label, 0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("infer instance preview class map failed: imageDir={}, annoDir={}", imageDir, annoDir, e);
        }
        return ret;
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
        boolean hasAnyMatched = candidates.values().stream().anyMatch(list -> list != null && !list.isEmpty());
        List<String> fallbackImages = hasAnyMatched ? Collections.emptyList() : sampleAnnotatedImages(imageDir, annoDir, perLabel, random);
        if (!hasAnyMatched && fallbackImages.isEmpty()) {
            fallbackImages = sampleAnyImages(imageDir, perLabel, random);
        }
        for (String label : result.keySet()) {
            List<String> files = candidates.getOrDefault(label, new ArrayList<>());
            if (files.isEmpty() && !fallbackImages.isEmpty()) {
                files = new ArrayList<>(fallbackImages);
            }
            Collections.shuffle(files, random);
            result.put(label, files.size() > perLabel ? new ArrayList<>(files.subList(0, perLabel)) : files);
        }
        if (result.isEmpty() && !fallbackImages.isEmpty()) {
            result.put("随机样例", fallbackImages);
        }
        return result;
    }

    private List<String> sampleAnnotatedImages(Path imageDir, Path annoDir, int perLabel, Random random) {
        if (!Files.isDirectory(imageDir) || !Files.isDirectory(annoDir)) return Collections.emptyList();
        LinkedHashSet<String> rels = new LinkedHashSet<>();
        try (var walk = Files.walk(annoDir)) {
            for (Path txt : walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).toList()) {
                if (parseDotaFile(txt).isEmpty()) continue;
                Path img = tryFindImageFile(imageDir, baseName(txt.getFileName().toString()));
                if (img != null) rels.add(imageDir.relativize(img).toString().replace("\\", "/"));
            }
        } catch (Exception e) {
            log.warn("sample annotated txt images failed: {}", annoDir, e);
        }
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
                if (!images.isArray() || !annotations.isArray()) continue;
                Map<Long, String> imageById = new HashMap<>();
                for (JsonNode image : images) {
                    long id = image.path("id").asLong(Long.MIN_VALUE);
                    String fileName = image.path("file_name").asText("");
                    if (id != Long.MIN_VALUE && StringUtils.hasText(fileName)) imageById.put(id, fileName);
                }
                for (JsonNode anno : annotations) {
                    long imageId = anno.path("image_id").asLong(Long.MIN_VALUE);
                    String fileName = imageById.get(imageId);
                    String resolvedName = resolveImageNameForPreview(imageDir, fileName);
                    if (StringUtils.hasText(resolvedName)) rels.add(resolvedName);
                }
            }
        } catch (Exception e) {
            log.warn("sample annotated coco images failed: {}", annoDir, e);
        }
        List<String> list = new ArrayList<>(rels);
        Collections.shuffle(list, random);
        return list.size() > perLabel ? new ArrayList<>(list.subList(0, perLabel)) : list;
    }
    private List<String> sampleAnyImages(Path imageDir, int perLabel, Random random) {
        if (!Files.isDirectory(imageDir)) return Collections.emptyList();
        try (var walk = Files.walk(imageDir)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".bmp") || n.endsWith(".tif") || n.endsWith(".tiff") || n.endsWith(".webp");
                    })
                    .toList();
            List<String> rels = new ArrayList<>();
            for (Path file : files) {
                rels.add(imageDir.relativize(file).toString().replace("\\", "/"));
            }
            Collections.shuffle(rels, random);
            return rels.size() > perLabel ? new ArrayList<>(rels.subList(0, perLabel)) : rels;
        } catch (Exception e) {
            log.warn("sample fallback images failed: {}", imageDir, e);
            return Collections.emptyList();
        }
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

    private List<DotaObject> readObjectsForImage(Path imageDir, Path annoDir, Path imgPath) {
        List<DotaObject> objects = new ArrayList<>();
        String base = baseName(imgPath.getFileName().toString());
        Path txtPath = annoDir.resolve(base + ".txt").normalize();
        if (txtPath.startsWith(annoDir) && Files.isRegularFile(txtPath)) {
            return parseDotaFile(txtPath);
        }
        objects.addAll(readCocoObjectsForImage(imageDir, annoDir, imgPath));
        return objects;
    }

    private List<DotaObject> readCocoObjectsForImage(Path imageDir, Path annoDir, Path imgPath) {
        List<DotaObject> out = new ArrayList<>();
        if (!Files.isDirectory(annoDir)) return out;
        String relUnix = imageDir.relativize(imgPath).toString().replace("\\", "/");
        String fileName = imgPath.getFileName().toString();
        String baseName = baseName(fileName);
        try (var walk = Files.walk(annoDir)) {
            for (Path json : walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                out.addAll(readCocoObjectsFromFile(json, relUnix, fileName, baseName));
                if (!out.isEmpty()) return out;
            }
        } catch (Exception e) {
            log.warn("read instance coco preview objects failed", e);
        }
        return out;
    }

    private List<DotaObject> readCocoObjectsFromFile(Path jsonFile, String relUnix, String fileName, String baseName) {
        List<DotaObject> out = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(Files.readString(jsonFile, StandardCharsets.UTF_8));
            JsonNode images = root.path("images");
            JsonNode annotations = root.path("annotations");
            JsonNode categories = root.path("categories");
            if (!images.isArray() || !annotations.isArray() || !categories.isArray()) return out;

            Set<Long> imageIds = new LinkedHashSet<>();
            for (JsonNode image : images) {
                long id = image.path("id").asLong(Long.MIN_VALUE);
                String oneFile = image.path("file_name").asText("").replace("\\", "/");
                if (id == Long.MIN_VALUE || !StringUtils.hasText(oneFile)) continue;
                String oneName = Paths.get(oneFile).getFileName().toString();
                String oneBase = baseName(oneName);
                if (oneFile.equals(relUnix) || oneName.equals(fileName) || oneBase.equals(baseName)) {
                    imageIds.add(id);
                }
            }
            if (imageIds.isEmpty()) return out;

            Map<Long, String> catById = new HashMap<>();
            for (JsonNode c : categories) {
                long id = c.path("id").asLong(Long.MIN_VALUE);
                String name = c.path("name").asText("unknown");
                if (id != Long.MIN_VALUE) catById.put(id, name);
            }

            for (JsonNode a : annotations) {
                long imageId = a.path("image_id").asLong(Long.MIN_VALUE);
                long catId = a.path("category_id").asLong(Long.MIN_VALUE);
                if (!imageIds.contains(imageId)) continue;
                DotaObject obj = cocoAnnotationToDotaObject(a, catById.getOrDefault(catId, "unknown"));
                if (obj != null) out.add(obj);
            }
        } catch (Exception e) {
            log.warn("read coco objects file failed: {}", jsonFile, e);
        }
        return out;
    }

    private DotaObject cocoAnnotationToDotaObject(JsonNode annotation, String label) {
        List<double[]> pts = new ArrayList<>();
        JsonNode segmentation = annotation.path("segmentation");
        if (segmentation.isArray() && segmentation.size() > 0) {
            JsonNode polygon = segmentation.get(0).isArray() ? segmentation.get(0) : segmentation;
            if (polygon != null && polygon.isArray() && polygon.size() >= 6) {
                for (int i = 0; i + 1 < polygon.size(); i += 2) {
                    pts.add(new double[]{polygon.get(i).asDouble(0d), polygon.get(i + 1).asDouble(0d)});
                }
            }
        }
        if (pts.isEmpty()) {
            JsonNode bbox = annotation.path("bbox");
            if (!bbox.isArray() || bbox.size() < 4) return null;
            double x = bbox.get(0).asDouble(0d);
            double y = bbox.get(1).asDouble(0d);
            double w = Math.max(0d, bbox.get(2).asDouble(0d));
            double h = Math.max(0d, bbox.get(3).asDouble(0d));
            pts.add(new double[]{x, y});
            pts.add(new double[]{x + w, y});
            pts.add(new double[]{x + w, y + h});
            pts.add(new double[]{x, y + h});
        }
        while (pts.size() < 4) pts.add(pts.get(pts.size() - 1));
        DotaObject obj = new DotaObject();
        obj.setX1(pts.get(0)[0]); obj.setY1(pts.get(0)[1]);
        obj.setX2(pts.get(1)[0]); obj.setY2(pts.get(1)[1]);
        obj.setX3(pts.get(2)[0]); obj.setY3(pts.get(2)[1]);
        obj.setX4(pts.get(3)[0]); obj.setY4(pts.get(3)[1]);
        obj.setName(label);
        obj.setPoints(Arrays.asList(
                Arrays.asList(obj.getX1(), obj.getY1()),
                Arrays.asList(obj.getX2(), obj.getY2()),
                Arrays.asList(obj.getX3(), obj.getY3()),
                Arrays.asList(obj.getX4(), obj.getY4())
        ));
        return obj;
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
                obj.setPoints(Arrays.asList(
                        Arrays.asList(obj.getX1(), obj.getY1()),
                        Arrays.asList(obj.getX2(), obj.getY2()),
                        Arrays.asList(obj.getX3(), obj.getY3()),
                        Arrays.asList(obj.getX4(), obj.getY4())
                ));
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
    }    @Data
    public static class InstancePreviewResp {
        private Long instanceDatasetId;
        private Integer perLabel;
        private List<InstancePreviewItem> items;
    }

    @Data
    public static class InstancePreviewItem {
        private String label;
        private Integer count;
        private List<InstancePreviewImage> images;
    }

    @Data
    public static class InstancePreviewImage {
        private String url;
        private String fileName;
        private int width;
        private int height;
        private List<DotaObject> objects;
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
        private List<List<Double>> points;
    }
}
