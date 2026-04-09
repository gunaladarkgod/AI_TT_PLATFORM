package com.xgls.web.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.entity.EngineLabel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 解析 CVAT annotations.xml -> 写入 img_info / anno_info
 * 同时可按需导出 DOTA-txt；并返回汇总统计用于 original_dataset
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CvatXmlIngestService {

    private final JdbcTemplate jdbcTemplate;
    private final EngineLabelService engineLabelService;

    /** 入库+导出结果（用于 original_dataset 汇总） */
    public static class IngestResult {
        public int imageCount;
        public int annoCount;
        public Map<String, Integer> classCounts = new LinkedHashMap<String, Integer>();
    }

    /**
     * 解析 annotations.xml 并批量写入 img_info / anno_info；生成 DOTA 每图1txt
     *
     * @param projectId 项目ID
     * @param annotationsXmlPath /annotations/annotations.xml
     * @param imagesDir /images
     * @param annoDir /annotations
     * @param writeDotaTxt 是否生成 DOTA txt（无标注也生成空文件）
     */
    public IngestResult ingest(long projectId,
                               Path annotationsXmlPath,
                               Path imagesDir,
                               Path annoDir,
                               boolean writeDotaTxt) {

        IngestResult summary = new IngestResult();

        try {
            if (annotationsXmlPath == null || !Files.exists(annotationsXmlPath)) {
                log.warn("annotations.xml not found: {}", annotationsXmlPath);
                return summary;
            }

            // 1) 解析 XML
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(annotationsXmlPath.toFile());
            doc.getDocumentElement().normalize();

            // 标签名 -> label_id
            Map<String, Integer> labelIdMap = loadLabelIdMap(projectId);

            // 2) 读取所有 <image>
            NodeList imageNodes = doc.getElementsByTagName("image");

            List<ImageRow> images = new ArrayList<ImageRow>(imageNodes.getLength());
            Map<String, List<AnnoRow>> annosByImage = new LinkedHashMap<String, List<AnnoRow>>();

            for (int i = 0; i < imageNodes.getLength(); i++) {
                Element imgEl = (Element) imageNodes.item(i);

                // 可能包含相对子路径：images/000368.jpg 或 nested/xxx.jpg
                String rawImgName = imgEl.getAttribute("name");
                if (StrUtil.isBlank(rawImgName)) continue;

                // ✅ 统一规范化：仅文件名
                String imgName = normalizeImageName(rawImgName);

                int width = (int) getDoubleAttr(imgEl, "width", 0);
                int height = (int) getDoubleAttr(imgEl, "height", 0);

                // 关键：直接从 XML 读取 task_id
                Integer taskId = null;
                long tid = getLongAttr(imgEl, "task_id", -1);
                if (tid >= 0) taskId = (int) tid;

                images.add(new ImageRow(imgName, width, height, taskId));

                // <box>
                NodeList boxNodes = imgEl.getElementsByTagName("box");
                for (int j = 0; j < boxNodes.getLength(); j++) {
                    Element box = (Element) boxNodes.item(j);

                    String label = box.getAttribute("label");
                    int labelId = labelIdMap.containsKey(label) ? labelIdMap.get(label) : 0;

                    double xtl = getDoubleAttr(box, "xtl", 0);
                    double ytl = getDoubleAttr(box, "ytl", 0);
                    double xbr = getDoubleAttr(box, "xbr", 0);
                    double ybr = getDoubleAttr(box, "ybr", 0);
                    double rot = getDoubleAttr(box, "rotation", 0);

                    double[][] quad = rectToQuad(xtl, ytl, xbr, ybr, rot);

                    List<AnnoRow> list = annosByImage.get(imgName);
                    if (list == null) {
                        list = new ArrayList<AnnoRow>();
                        annosByImage.put(imgName, list);
                    }
                    list.add(AnnoRow.of(imgName, label, labelId, quad, taskId));

                    summary.annoCount++;
                    Integer old = summary.classCounts.get(label);
                    summary.classCounts.put(label, old == null ? 1 : (old + 1));
                }

                // <polygon>
                NodeList polyNodes = imgEl.getElementsByTagName("polygon");
                for (int j = 0; j < polyNodes.getLength(); j++) {
                    Element poly = (Element) polyNodes.item(j);

                    String label = poly.getAttribute("label");
                    int labelId = labelIdMap.containsKey(label) ? labelIdMap.get(label) : 0;

                    String pointsStr = poly.getAttribute("points");
                    List<double[]> pts = parsePoints(pointsStr);

                    double[][] quad;
                    if (pts.size() == 4) {
                        quad = new double[][]{pts.get(0), pts.get(1), pts.get(2), pts.get(3)};
                    } else if (pts.size() > 4) {
                        double minX = Double.POSITIVE_INFINITY;
                        double minY = Double.POSITIVE_INFINITY;
                        double maxX = Double.NEGATIVE_INFINITY;
                        double maxY = Double.NEGATIVE_INFINITY;

                        for (int k = 0; k < pts.size(); k++) {
                            double[] p = pts.get(k);
                            if (p[0] < minX) minX = p[0];
                            if (p[1] < minY) minY = p[1];
                            if (p[0] > maxX) maxX = p[0];
                            if (p[1] > maxY) maxY = p[1];
                        }
                        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
                            continue;
                        }
                        quad = rectToQuad(minX, minY, maxX, maxY, 0);
                    } else {
                        continue;
                    }

                    List<AnnoRow> list = annosByImage.get(imgName);
                    if (list == null) {
                        list = new ArrayList<AnnoRow>();
                        annosByImage.put(imgName, list);
                    }
                    list.add(AnnoRow.of(imgName, label, labelId, quad, taskId));

                    summary.annoCount++;
                    Integer old = summary.classCounts.get(label);
                    summary.classCounts.put(label, old == null ? 1 : (old + 1));
                }
            }

            // ✅ imageCount 用去重后的最终 img_name 数量
            Set<String> uniq = new LinkedHashSet<String>();
            for (int i = 0; i < images.size(); i++) {
                uniq.add(images.get(i).imgName);
            }
            summary.imageCount = uniq.size();

            // 3) 批量 upsert img_info（带 task_id）
            batchUpsertImages(projectId, images);

            // 4) 查询 image_id 映射
            Set<String> nameSet = new LinkedHashSet<String>();
            for (int i = 0; i < images.size(); i++) {
                nameSet.add(images.get(i).imgName);
            }
            Map<String, Long> imageIdMap = fetchImageIds(projectId, nameSet);

            // 5) 批量写 anno_info（带 task_id）
            List<AnnoRow> flat = new ArrayList<AnnoRow>();
            for (List<AnnoRow> l : annosByImage.values()) {
                flat.addAll(l);
            }
            int inserted = batchInsertAnnos(projectId, imageIdMap, flat);
            log.info("ingest done for project {}: images(unique)={}, annos inserted={}",
                    projectId, summary.imageCount, inserted);

            // 6) DOTA 每图 1 个 txt（无标注也生成空文件）
            if (writeDotaTxt) {
                Files.createDirectories(annoDir);

                LinkedHashSet<String> allImageNames = new LinkedHashSet<String>();
                for (int i = 0; i < images.size(); i++) {
                    allImageNames.add(images.get(i).imgName);
                }

                for (String name : allImageNames) {
                    List<AnnoRow> list = annosByImage.get(name);
                    if (list == null) list = Collections.emptyList();

                    Path txt = annoDir.resolve(baseName(name) + ".txt");
                    BufferedWriter bw = null;
                    try {
                        bw = Files.newBufferedWriter(txt, StandardCharsets.UTF_8);
                        for (int i = 0; i < list.size(); i++) {
                            AnnoRow a = list.get(i);
                            bw.write(String.format(Locale.ROOT,
                                    "%.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f %s 0",
                                    a.x1, a.y1, a.x2, a.y2, a.x3, a.y3, a.x4, a.y4, a.className));
                            bw.newLine();
                        }
                    } finally {
                        if (bw != null) {
                            try { bw.close(); } catch (Exception ignore) {}
                        }
                    }
                }
            }

            return summary;

        } catch (Exception e) {
            log.error("ingest xml failed, projectId={}, xml={}", projectId, annotationsXmlPath, e);
            return summary;
        }
    }

    /* =========================== DB ops =========================== */

    private void batchUpsertImages(long projectId, List<ImageRow> images) {
        if (images == null || images.isEmpty()) return;

        // 同名去重，优先保留带 task_id 的
        Map<String, ImageRow> dedup = new LinkedHashMap<String, ImageRow>();
        for (int i = 0; i < images.size(); i++) {
            ImageRow im = images.get(i);
            ImageRow oldV = dedup.get(im.imgName);
            if (oldV == null) {
                dedup.put(im.imgName, im);
            } else {
                if (oldV.taskId == null && im.taskId != null) {
                    dedup.put(im.imgName, im);
                }
            }
        }

        String sql =
                "INSERT INTO img_info (project_id, task_id, img_name, data_stage, width, height)\n" +
                        "VALUES (?, ?, ?, 0, ?, ?)\n" +
                        "ON DUPLICATE KEY UPDATE\n" +
                        "  task_id=VALUES(task_id),\n" +
                        "  width=VALUES(width),\n" +
                        "  height=VALUES(height)";

        jdbcTemplate.batchUpdate(sql, dedup.values(), 500, (ps, im) -> {
            ps.setLong(1, projectId);
            if (im.taskId == null) ps.setObject(2, null); else ps.setLong(2, im.taskId);
            ps.setString(3, im.imgName);
            ps.setInt(4, im.width);
            ps.setInt(5, im.height);
        });
    }

    private Map<String, Long> fetchImageIds(long projectId, Set<String> names) {
        Map<String, Long> map = new HashMap<String, Long>(names.size() * 2);
        if (names == null || names.isEmpty()) return map;

        List<String> all = new ArrayList<String>(names);
        int B = 800;

        for (int i = 0; i < all.size(); i += B) {
            List<String> sub = all.subList(i, Math.min(all.size(), i + B));

            StringBuilder qs = new StringBuilder();
            for (int k = 0; k < sub.size(); k++) {
                if (k > 0) qs.append(",");
                qs.append("?");
            }

            String sql = "SELECT id, img_name FROM img_info WHERE project_id=? AND img_name IN (" + qs + ")";

            List<Object> params = new ArrayList<Object>();
            params.add(projectId);
            params.addAll(sub);

            jdbcTemplate.query(sql, params.toArray(), rs -> {
                map.put(rs.getString("img_name"), rs.getLong("id"));
            });
        }
        return map;
    }

    private int batchInsertAnnos(long projectId, Map<String, Long> imageIdMap, List<AnnoRow> annos) {
        if (annos == null || annos.isEmpty()) return 0;

        String sql =
                "INSERT INTO anno_info\n" +
                        "  (project_id, task_id, image_id, img_name, data_stage,\n" +
                        "   class_name, label_id,\n" +
                        "   x1,y1,x2,y2,x3,y3,x4,y4, created_time)\n" +
                        "VALUES\n" +
                        "  (?,?,?,?,?,\n" +
                        "   ?,?,\n" +
                        "   ?,?,?,?,?,?,?,?, ?)\n" +
                        "ON DUPLICATE KEY UPDATE class_name=VALUES(class_name), task_id=VALUES(task_id)";

        Timestamp now = Timestamp.from(Instant.now());

        List<AnnoRow> filtered = annos.stream()
                .filter(a -> imageIdMap.containsKey(a.imgName))
                .collect(Collectors.toList());

        jdbcTemplate.batchUpdate(sql, filtered, 500, (ps, a) -> {
            ps.setLong(1, projectId);
            if (a.taskId == null) ps.setObject(2, null); else ps.setLong(2, a.taskId);
            ps.setLong(3, imageIdMap.get(a.imgName));
            ps.setString(4, a.imgName);
            ps.setInt(5, 0);
            ps.setString(6, a.className);
            ps.setInt(7, a.labelId);
            ps.setDouble(8, a.x1);  ps.setDouble(9, a.y1);
            ps.setDouble(10, a.x2); ps.setDouble(11, a.y2);
            ps.setDouble(12, a.x3); ps.setDouble(13, a.y3);
            ps.setDouble(14, a.x4); ps.setDouble(15, a.y4);
            ps.setTimestamp(16, now);
        });

        return filtered.size();
    }

    private Map<String, Integer> loadLabelIdMap(long projectId) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        try {
            LambdaQueryWrapper<EngineLabel> w = new LambdaQueryWrapper<EngineLabel>()
                    .eq(EngineLabel::getProject_id, (int) projectId);
            List<EngineLabel> labels = engineLabelService.list(w);
            for (int i = 0; i < labels.size(); i++) {
                EngineLabel e = labels.get(i);
                if (e.getName() != null) map.put(e.getName(), e.getId());
            }
        } catch (Exception ignore) {
        }
        return map;
    }

    /* =========================== 解析/几何/工具 =========================== */

    /**
     * ✅ 统一将 XML 的 image.name 规范化为“仅文件名”
     * 适配你当前 moveExportResultToStandardDirs 的“扁平拷贝”策略（不保留目录层级）
     */
    private static String normalizeImageName(String raw) {
        if (StrUtil.isBlank(raw)) return raw;

        String s = raw.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);

        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith("images/")) {
            s = s.substring("images/".length());
        }

        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);

        return s;
    }

    private static double getDoubleAttr(Element el, String name, double def) {
        try {
            String v = el.getAttribute(name);
            return StrUtil.isBlank(v) ? def : Double.parseDouble(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static long getLongAttr(Element el, String name, long def) {
        try {
            String v = el.getAttribute(name);
            return StrUtil.isBlank(v) ? def : Long.parseLong(v);
        } catch (Exception e) {
            return def;
        }
    }

    /** 解析 CVAT 多边形 points 属性："x1,y1;x2,y2;..." -> List<double[]{x,y}> */
    private static List<double[]> parsePoints(String s) {
        if (StrUtil.isBlank(s)) return Collections.emptyList();

        String[] pairs = s.split(";");
        List<double[]> out = new ArrayList<double[]>(pairs.length);

        for (int i = 0; i < pairs.length; i++) {
            String p = pairs[i];
            String[] xy = p.trim().split(",");
            if (xy.length != 2) continue;

            try {
                double x = Double.parseDouble(xy[0].trim());
                double y = Double.parseDouble(xy[1].trim());
                out.add(new double[]{x, y});
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** 把矩形(含 rotation 角度，度)转四点（顺时针） */
    private static double[][] rectToQuad(double xtl, double ytl, double xbr, double ybr, double deg) {
        double cx = (xtl + xbr) / 2.0;
        double cy = (ytl + ybr) / 2.0;

        double hw = Math.max(0, (xbr - xtl) / 2.0);
        double hh = Math.max(0, (ybr - ytl) / 2.0);

        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad), sin = Math.sin(rad);

        double[][] rel = new double[][]{{-hw, -hh}, {+hw, -hh}, {+hw, +hh}, {-hw, +hh}};
        double[][] out = new double[4][2];

        for (int i = 0; i < 4; i++) {
            double x = rel[i][0], y = rel[i][1];
            double rx = x * cos - y * sin;
            double ry = x * sin + y * cos;
            out[i][0] = cx + rx;
            out[i][1] = cy + ry;
        }
        return out;
    }

    private static String baseName(String pathLike) {
        String fn = pathLike.replace('\\', '/');
        int slash = fn.lastIndexOf('/');
        if (slash >= 0) fn = fn.substring(slash + 1);
        int dot = fn.lastIndexOf('.');
        return dot >= 0 ? fn.substring(0, dot) : fn;
    }

    /* =========================== 小 DTO =========================== */

    private static class ImageRow {
        String imgName;
        int width;
        int height;
        Integer taskId;

        ImageRow(String imgName, int width, int height, Integer taskId) {
            this.imgName = imgName;
            this.width = width;
            this.height = height;
            this.taskId = taskId;
        }
    }

    private static class AnnoRow {
        String imgName, className;
        int labelId;
        double x1, y1, x2, y2, x3, y3, x4, y4;
        Integer taskId;

        static AnnoRow of(String imgName, String cls, int labelId, double[][] q, Integer taskId) {
            AnnoRow a = new AnnoRow();
            a.imgName = imgName;
            a.className = cls;
            a.labelId = labelId;

            a.x1 = q[0][0]; a.y1 = q[0][1];
            a.x2 = q[1][0]; a.y2 = q[1][1];
            a.x3 = q[2][0]; a.y3 = q[2][1];
            a.x4 = q[3][0]; a.y4 = q[3][1];

            a.taskId = taskId;
            return a;
        }
    }
}
