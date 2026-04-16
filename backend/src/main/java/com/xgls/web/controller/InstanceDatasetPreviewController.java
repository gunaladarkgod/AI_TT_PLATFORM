package com.xgls.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.mapper.InstanceDatasetMapper;
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

    private Map<String, List<String>> samplePerLabel(Path imageDir, Path annoDir, Set<String> labels, int perLabel) {
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

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(annoDir, "*.txt")) {
            for (Path txt : ds) {
                List<DotaObject> objects = parseDotaFile(txt);
                Set<String> matched = new LinkedHashSet<>();
                for (DotaObject obj : objects) {
                    if (labels.contains(obj.getName())) {
                        matched.add(obj.getName());
                    }
                }
                if (matched.isEmpty()) continue;

                String base = baseName(txt.getFileName().toString());
                Path img = tryFindImageFile(imageDir, base);
                if (img == null) continue;
                String imgName = img.getFileName().toString();
                for (String label : matched) {
                    candidates.get(label).add(imgName);
                }
            }
        } catch (Exception e) {
            log.warn("采样预览失败: imageDir={}, annoDir={}", imageDir, annoDir, e);
        }

        Random random = new Random();
        for (String label : labels) {
            List<String> files = candidates.getOrDefault(label, new ArrayList<>());
            Collections.shuffle(files, random);
            result.put(label, files.size() > perLabel ? new ArrayList<>(files.subList(0, perLabel)) : files);
        }
        return result;
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
