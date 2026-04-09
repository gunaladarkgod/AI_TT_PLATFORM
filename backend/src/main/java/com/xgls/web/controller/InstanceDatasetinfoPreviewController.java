package com.xgls.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.web.entity.InstanceDatasetinfo;
import com.xgls.web.mapper.InstanceDatasetinfoMapper;
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
 * 实例数据集 - 示例预览 + DOTA 标注框
 *
 * 前端可以调用：
 *  1）GET /instanceDataset/{id}/preview?perLabel=3&part=train|test
 *      -> 返回每个类别最多 perLabel 张图片 URL
 *  2）GET /instanceDataset/{id}/image?img=xxx.png&part=train|test
 *      -> 返回图片二进制
 *  3）GET /instanceDataset/{id}/objects?img=xxx.png&part=train|test
 *      -> 返回该图片的 DOTA 标注点，用于画蓝色框
 */
@Slf4j
@RestController
@RequestMapping("/instanceDataset")
public class InstanceDatasetinfoPreviewController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private InstanceDatasetinfoMapper instanceDatasetinfoMapper;

    // ============ 1. 示例预览：每个类别最多 perLabel 张图 ============
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

        InstanceDatasetinfo ds = instanceDatasetinfoMapper.selectById(id);
        if (ds == null) {
            return buildError("实例数据集不存在，id=" + id);
        }

        // 选择 train / test
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

        // class_list 解析成 { label -> count }，这里 count 只是统计用，预览主要是要 label 名
        Map<String, Integer> classMap = parseClassList(classListStr);

        // 按类别抽样：每个 label 最多 perLabel 张
        Map<String, List<String>> label2Images = samplePerLabel(
                Paths.get(imageDir),
                Paths.get(annoDir),
                classMap.keySet(),
                perLabel
        );

        // 拼接可访问的图片 URL：/instanceDataset/{id}/image?part=xxx&img=xxx.png
        String base = getBaseUrl(request)
                + "/instanceDataset/" + id + "/image?part=" + part + "&img=";

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

    // ============ 2. 返回具体图片二进制 ============
    @GetMapping("/{id}/image")
    public void image(
            @PathVariable("id") Long id,
            @RequestParam("img") String img,
            @RequestParam(value = "part", required = false, defaultValue = "train") String part,
            HttpServletResponse response
    ) throws IOException {
        InstanceDatasetinfo ds = instanceDatasetinfoMapper.selectById(id);
        if (ds == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String imageDir;
        if ("test".equalsIgnoreCase(part)) {
            imageDir = ds.getTestImagePath();
        } else {
            imageDir = ds.getTrainImagePath();
        }

        if (!StringUtils.hasText(imageDir)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Path dir = Paths.get(imageDir);
        Path imgPath = dir.resolve(img);
        if (!Files.exists(imgPath)) {
            // 如果前端只传了不带后缀的名字，尝试自动补充常见后缀
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

    // ============ 3. 单张图片的 DOTA 标注（给前端画蓝框） ============
    @GetMapping("/{id}/objects")
    public Map<String, Object> objects(
            @PathVariable("id") Long id,
            @RequestParam("img") String img,
            @RequestParam(value = "part", required = false, defaultValue = "train") String part
    ) {
        InstanceDatasetinfo ds = instanceDatasetinfoMapper.selectById(id);
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

        if (!StringUtils.hasText(imageDir) || !StringUtils.hasText(annoDir)) {
            return buildError("实例数据集缺少图片或标注路径");
        }

        Path imgDir = Paths.get(imageDir);
        Path annoDirPath = Paths.get(annoDir);

        // 找到图片真实路径
        Path imgPath = imgDir.resolve(img);
        if (!Files.exists(imgPath)) {
            imgPath = tryFindImageFile(imgDir, baseName(img));
        }
        if (imgPath == null || !Files.exists(imgPath)) {
            return buildError("图片不存在: " + img);
        }

        // DOTA 标注 txt 路径：与图片同名
        String base = baseName(imgPath.getFileName().toString());
        Path txtPath = annoDirPath.resolve(base + ".txt");
        if (!Files.exists(txtPath)) {
            return buildError("标注文件不存在: " + txtPath);
        }

        // 读取图片尺寸
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

    // ======================================
    // 通用返回结构 {code, msg, data, success}
    // ======================================

    private Map<String, Object> buildOk(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "请求成功");
        m.put("data", data);
        m.put("success", true);
        return m;
    }

    private Map<String, Object> buildError(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 1);
        m.put("msg", msg);
        m.put("data", null);
        m.put("success", false);
        return m;
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    // ======================================
    // class_list 解析
    // ======================================

    /**
     * 解析 class_list：
     *  支持两种形式：
     *   1) {"彩色年":2219,"俄方":551}
     *   2) ["彩色年","俄方"]
     *   3) 外面再包一层字符串都能处理
     */
    private Map<String, Integer> parseClassList(String classListStr) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(classListStr)) {
            return map;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(classListStr);
            if (node.isTextual()) {
                // 再解一层
                node = OBJECT_MAPPER.readTree(node.asText());
            }

            if (node.isObject()) {
                // {"彩色年":2219,"俄方":551}
                Iterator<Map.Entry<String, JsonNode>> it = node.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String name = e.getKey();
                    int cnt = e.getValue().asInt(0);
                    map.put(name, cnt);
                }
            } else if (node.isArray()) {
                // ["car","truck"] -> count 先给 0
                for (JsonNode item : node) {
                    if (item.isTextual()) {
                        map.put(item.asText(), 0);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 class_list 失败: {}", classListStr, e);
        }
        return map;
    }

    // ======================================
    // DOTA 相关工具方法
    // ======================================

    /**
     * 按类别抽样：每个 label 最多取 perLabel 张图片
     *
     * @param imageDir 图片目录
     * @param annoDir  标注 txt 目录
     * @param labels   希望展示的类别集合（可以为 null，表示所有在标注中出现的类别）
     */
    private Map<String, List<String>> samplePerLabel(
            Path imageDir,
            Path annoDir,
            Collection<String> labels,
            int perLabel
    ) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        Set<String> labelSet = (labels == null) ? null : new HashSet<>(labels);
        if (perLabel <= 0) perLabel = 3;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(annoDir, "*.txt")) {
            for (Path txt : stream) {
                String base = baseName(txt.getFileName().toString());

                Path imgPath = tryFindImageFile(imageDir, base);
                if (imgPath == null || !Files.exists(imgPath)) {
                    continue;
                }

                List<DotaObject> objs = parseDotaFile(txt);
                if (objs.isEmpty()) {
                    continue;
                }

                // 这张图片里出现过哪些类别
                Set<String> labelsInImage = new HashSet<>();
                for (DotaObject obj : objs) {
                    String label = obj.getLabel();
                    if (!StringUtils.hasText(label)) continue;
                    if (labelSet != null && !labelSet.contains(label)) {
                        continue;
                    }
                    labelsInImage.add(label);
                }

                if (labelsInImage.isEmpty()) {
                    continue;
                }

                boolean allFull = true;
                for (String lb : labelsInImage) {
                    List<String> list = result.computeIfAbsent(lb, k -> new ArrayList<>());
                    if (list.size() < perLabel) {
                        list.add(imgPath.getFileName().toString()); // 只存文件名，后面拼 URL
                    }
                    if (list.size() < perLabel) {
                        allFull = false;
                    }
                }

                if (allFull && labelSet != null && result.keySet().containsAll(labelSet)) {
                    // 所有需要展示的类别都凑够了 perLabel 张
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("samplePerLabel 扫描 DOTA 标注失败: {}", annoDir, e);
        }

        // 确保所有 label 至少有空列表
        if (labelSet != null) {
            for (String lb : labelSet) {
                result.computeIfAbsent(lb, k -> new ArrayList<>());
            }
        }
        return result;
    }

    /**
     * 解析单个 DOTA txt 文件
     * 每行格式：x1 y1 x2 y2 x3 y3 x4 y4 category difficult
     * 前两行 DOTA 里常见的 imagesource/gsd 会自动跳过
     */
    private List<DotaObject> parseDotaFile(Path txtPath) {
        List<DotaObject> list = new ArrayList<>();
        if (!Files.exists(txtPath)) return list;

        try {
            List<String> lines = Files.readAllLines(txtPath);
            for (String line : lines) {
                if (!StringUtils.hasText(line)) continue;

                line = line.trim();
                // 跳过 imagesource:xxx / gsd:xxx
                if (line.startsWith("imagesource:") || line.startsWith("gsd:")) {
                    continue;
                }

                String[] arr = line.split("\\s+");
                if (arr.length < 9) continue;

                try {
                    double x1 = Double.parseDouble(arr[0]);
                    double y1 = Double.parseDouble(arr[1]);
                    double x2 = Double.parseDouble(arr[2]);
                    double y2 = Double.parseDouble(arr[3]);
                    double x3 = Double.parseDouble(arr[4]);
                    double y3 = Double.parseDouble(arr[5]);
                    double x4 = Double.parseDouble(arr[6]);
                    double y4 = Double.parseDouble(arr[7]);
                    String category = arr[8];
                    int difficult = 0;
                    if (arr.length >= 10) {
                        difficult = Integer.parseInt(arr[9]);
                    }

                    DotaObject obj = new DotaObject();
                    obj.setLabel(category);
                    obj.setDifficult(difficult);

                    List<List<Double>> points = new ArrayList<>();
                    points.add(Arrays.asList(x1, y1));
                    points.add(Arrays.asList(x2, y2));
                    points.add(Arrays.asList(x3, y3));
                    points.add(Arrays.asList(x4, y4));
                    obj.setPoints(points);

                    list.add(obj);
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            log.warn("解析 DOTA 文件失败: {}", txtPath, e);
        }
        return list;
    }

    /**
     * 根据 baseName 在目录下按常见后缀查找图片
     */
    private Path tryFindImageFile(Path imageDir, String base) {
        String[] exts = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};
        for (String ext : exts) {
            Path p = imageDir.resolve(base + ext);
            if (Files.exists(p)) {
                return p;
            }
        }
        // 兜底：通配搜索
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(imageDir, base + ".*")) {
            for (Path p : ds) {
                return p;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private String baseName(String fileName) {
        if (!StringUtils.hasText(fileName)) return fileName;
        int idx = fileName.lastIndexOf('.');
        if (idx > 0) {
            return fileName.substring(0, idx);
        }
        return fileName;
    }

    // ======================================
    // DTO
    // ======================================

    @Data
    public static class InstancePreviewResp {
        private Long instanceDatasetId;
        private Integer perLabel;
        private List<InstancePreviewItem> items;
    }

    @Data
    public static class InstancePreviewItem {
        private String label;
        private List<String> images;
        private Integer count;
    }

    @Data
    public static class DotaImageObjects {
        private int width;
        private int height;
        private List<DotaObject> objects;
    }

    @Data
    public static class DotaObject {
        private String label;
        private int difficult;
        // [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]
        private List<List<Double>> points;
    }
}
