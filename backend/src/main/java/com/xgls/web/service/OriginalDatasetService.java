package com.xgls.web.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.mapper.OriginalDatasetMapper;
import com.xgls.web.mapper.TaskDatasetMapper;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.vo.dataset.MarkSubsetsReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OriginalDataset 相关服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OriginalDatasetService extends ServiceImpl<OriginalDatasetMapper, OriginalDataset> {

    private final JdbcTemplate jdbcTemplate;
    private final TaskDatasetMapper taskDatasetMapper;

    /** 可选：对外基础 URL（如 http://127.0.0.1:8081），若不配置将使用默认 */
    @Value("${sys.public-base-url:}")
    private String publicBaseUrl;

    /** 当库里 data_path 为空时的回落目录（你提供的实际根） */
    private static final Path DEFAULT_DATA_ROOT =
            Paths.get("/home/cs303-1/AI_TT_Platform/data/original_dataset");

    /* ====================== 任务数据集：标记 + 物化 ====================== */

    /**
     * 标记 original_dataset 的 type_mark（目标=1，预训练=0），
     * 基于本次选择聚合插入一条 task_dataset，
     * 然后将 original_dataset 全表的 type_mark 重置为 2。
     */
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult markAndMaterializeTaskDataset(MarkSubsetsReq req) {
        // 1) 解析请求（去重 & 冲突消解：目标优先）
        List<Long> reqTarget = (req != null && req.getTarget() != null) ? req.getTarget() : Collections.<Long>emptyList();
        List<Long> reqTrain  = (req != null && req.getTrain()  != null) ? req.getTrain()  : Collections.<Long>emptyList();

        LinkedHashSet<Long> targetSet = new LinkedHashSet<Long>(reqTarget);
        LinkedHashSet<Long> trainSet  = new LinkedHashSet<Long>(reqTrain);
        trainSet.removeAll(targetSet);

        if (targetSet.isEmpty() && trainSet.isEmpty()) {
            return AjaxResult.error("请选择至少一个子集");
        }

        // 2) 先按本次选择更新 type_mark（目标=1、预训练=0）供聚合计算
        if (!targetSet.isEmpty()) {
            this.lambdaUpdate()
                    .in(OriginalDataset::getId, targetSet)
                    .set(OriginalDataset::getTypeMark, 1)
                    .set(OriginalDataset::getUpdatedTime, LocalDateTime.now())
                    .update();
        }
        if (!trainSet.isEmpty()) {
            this.lambdaUpdate()
                    .in(OriginalDataset::getId, trainSet)
                    .set(OriginalDataset::getTypeMark, 0)
                    .set(OriginalDataset::getUpdatedTime, LocalDateTime.now())
                    .update();
        }

        // 3) 读出本次涉及的 original_dataset 记录
        List<Long> allIds = new ArrayList<Long>();
        allIds.addAll(targetSet);
        allIds.addAll(trainSet);

        List<OriginalDataset> rows = allIds.isEmpty() ? Collections.<OriginalDataset>emptyList() : this.listByIds(allIds);
        Map<Long, OriginalDataset> byId = rows.stream().collect(Collectors.toMap(OriginalDataset::getId, x -> x));

        // 4) 计算聚合结果
        String taskName = StrUtil.isNotBlank(req.getName()) ? req.getName() : StrUtil.trimToNull(req.getFatherName());
        if (StrUtil.isBlank(taskName)) {
            return AjaxResult.error("任务数据集名称不能为空");
        }

        String sensorType = pickSensorType(targetSet, trainSet, byId);
        Aggregate aggCore = aggregateFor(targetSet, byId); // 目标集合
        Aggregate aggSup  = aggregateFor(trainSet,  byId); // 预训练集合

        String targetType = null; // 此处不确定，置空

        String username = (SessionUtil.getCurUser() != null && StrUtil.isNotBlank(SessionUtil.getCurUser().getUsername()))
                ? SessionUtil.getCurUser().getUsername()
                : null;
        LocalDateTime now = LocalDateTime.now();

        // 5) 组装并插入 TaskDataset
        TaskDataset td = new TaskDataset();
        td.setName(taskName);
        td.setSensorType(sensorType);
        td.setTargetType(targetType);
        td.setDataFormat(0);
        td.setUsername(username);
        td.setCreatedTime(now);

        // core*
        td.setCoreId(joinUnderScore(aggCore.ids));
        td.setCoreName(joinUnderScore(aggCore.names));
        td.setCoreTargetType(deriveTargetType(aggCore.classKeys));
        td.setCoreImgNum(aggCore.imgSum);
        td.setCoreAnnoNum(aggCore.annoSum);
        td.setCoreClassNum(aggCore.classCount);
        td.setCoreClassList(JSONUtil.toJsonStr(aggCore.classMap));
        td.setCoreDataPath(null);
        td.setCoreAnnoPath(null);

        // sup*
        td.setSupId(joinUnderScore(aggSup.ids));
        td.setSupName(joinUnderScore(aggSup.names));
        td.setSupTargetType(deriveTargetType(aggSup.classKeys));
        td.setSupImgNum(aggSup.imgSum);
        td.setSupAnnoNum(aggSup.annoSum);
        td.setSupClassNum(aggSup.classCount);
        td.setSupClassList(JSONUtil.toJsonStr(aggSup.classMap));
        td.setSupDataPath(null);
        td.setSupAnnoPath(null);

        int ins = taskDatasetMapper.insert(td);
        if (ins <= 0) {
            throw new IllegalStateException("生成任务数据集失败");
        }

        // 6) 插入成功后：将 original_dataset 全表 type_mark 重置为 2
        this.lambdaUpdate()
                .set(OriginalDataset::getTypeMark, 2)
                .set(OriginalDataset::getUpdatedTime, LocalDateTime.now())
                .update();

        // 7) 返回简单结果
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("taskDatasetId", td.getId());
        data.put("targetUpdated", targetSet.size());
        data.put("trainUpdated",  trainSet.size());
        return AjaxResult.success(data);
    }

    /* ====================== 列表 & 全量 ====================== */

    public Page<OriginalDataset> pageList(Long projectId,
                                          Integer dataFormat,
                                          String keyword,
                                          Integer page,
                                          Integer size,
                                          String sortBy,
                                          boolean desc) {

        long pageNo   = (page == null || page < 1) ? 1 : page;
        long pageSize = (size == null || size < 1) ? 20 : size;

        LambdaQueryWrapper<OriginalDataset> wrapper = new LambdaQueryWrapper<OriginalDataset>();
        if (projectId != null) {
            wrapper.eq(OriginalDataset::getProjectId, projectId);
        }
        if (dataFormat != null) {
            wrapper.eq(OriginalDataset::getDataFormat, dataFormat);
        }
        if (StrUtil.isNotBlank(keyword)) {
            final String kw = keyword.trim();
            wrapper.and(w -> w.like(OriginalDataset::getName, kw)
                    .or()
                    .like(OriginalDataset::getProjectName, kw));
        }

        Map<String, String> allow = new LinkedHashMap<String, String>();
        allow.put("id", "id");
        allow.put("created_time", "created_time");
        allow.put("updated_time", "updated_time");
        allow.put("img_num", "img_num");
        allow.put("anno_num", "anno_num");
        allow.put("class_num", "class_num");

        Page<OriginalDataset> p = new Page<OriginalDataset>(pageNo, pageSize);

        String sortKey = sortBy == null ? "" : sortBy;
        String col = allow.getOrDefault(sortKey.toLowerCase(Locale.ROOT), "created_time");

        if (desc) p.addOrder(OrderItem.desc(col)); else p.addOrder(OrderItem.asc(col));
        return this.page(p, wrapper);
    }

    public List<OriginalDataset> listAll(Long projectId, Integer dataFormat) {
        LambdaQueryWrapper<OriginalDataset> wrapper = new LambdaQueryWrapper<OriginalDataset>();
        if (projectId != null) wrapper.eq(OriginalDataset::getProjectId, projectId);
        if (dataFormat != null) wrapper.eq(OriginalDataset::getDataFormat, dataFormat);
        wrapper.orderByDesc(OriginalDataset::getCreatedTime);
        return this.list(wrapper);
    }

    /* ====================== 预览接口（返回绝对 URL） ====================== */

    /**
     * 返回：每个标签各取 perLabel 张示例图片（绝对 URL）
     * @param baseUrl 形如 http://127.0.0.1:8081（可传 null，内部会回落）
     */
    public AjaxResult previewSamples(Long datasetId, Integer perLabel, String baseUrl) {
        if (datasetId == null) return AjaxResult.error("datasetId 为空");

        int n = (perLabel == null || perLabel <= 0) ? 3 : Math.min(perLabel, 12);

        OriginalDataset od = this.getById(datasetId);
        if (od == null) return AjaxResult.error("数据集不存在");

        Set<String> labels = parseClassKeys(od.getClassList());
        if (labels.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("datasetId", datasetId);
            payload.put("perLabel", n);
            payload.put("items", Collections.emptyList());
            return AjaxResult.success(payload);
        }

        List<Long> taskIdList = parseTaskIds(od.getTaskId());
        String base = ensureBaseUrl(baseUrl);

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (String label : labels) {
            List<String> names = pickImagesForLabel(od.getProjectId(), taskIdList, label, n);

            List<String> urls = names.stream()
                    .map(img -> base + "/original-dataset/" + datasetId + "/image?img=" + urlEncode(img))
                    .collect(Collectors.toList());

            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("label", label);
            one.put("images", urls);
            one.put("count", urls.size());
            items.add(one);
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("datasetId", datasetId);
        payload.put("perLabel", n);
        payload.put("items", items);
        return AjaxResult.success(payload);
    }

    /** 兼容旧调用：自动推断 baseUrl（优先配置项，其次默认 http://127.0.0.1:8081） */
    public AjaxResult previewSamples(Long datasetId, Integer perLabel) {
        return previewSamples(datasetId, perLabel, null);
    }

    /* ====================== 图片直出 ====================== */

    /**
     * 图片二进制直出：基于 original_dataset.data_path + img_name
     * 当 data_path 为空时，回落到 DEFAULT_DATA_ROOT/{projectId}/images
     */
    public void streamImage(Long datasetId, String imgName, jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
        if (datasetId == null || StrUtil.isBlank(imgName)) { resp.setStatus(404); return; }

        OriginalDataset od = this.getById(datasetId);
        if (od == null) { resp.setStatus(404); return; }

        // ✅ 只做一次“安全规范化”，避免二次 decode / 目录穿越 / images/ 前缀重复
        String safeName = normalizeImgParam(imgName);
        if (safeName == null) { resp.setStatus(404); return; }

        Path base;
        if (StrUtil.isNotBlank(od.getDataPath())) {
            // data_path 约定指向 images 目录：.../original_dataset/{projectId}/images
            base = Paths.get(od.getDataPath()).normalize();
        } else {
            // ✅ 回退：original_dataset_root/{projectId}/images
            base = DEFAULT_DATA_ROOT
                    .resolve(String.valueOf(od.getProjectId()))
                    .resolve("images")
                    .normalize();
        }

        Path file = base.resolve(safeName).normalize();

        if (!file.startsWith(base) || !Files.exists(file) || Files.isDirectory(file)) {
            resp.setStatus(404); return;
        }

        String ctype = Files.probeContentType(file);
        if (StrUtil.isBlank(ctype)) {
            String fn = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fn.endsWith(".jpg") || fn.endsWith(".jpeg")) ctype = "image/jpeg";
            else if (fn.endsWith(".png")) ctype = "image/png";
            else if (fn.endsWith(".bmp")) ctype = "image/bmp";
            else if (fn.endsWith(".webp")) ctype = "image/webp";
            else ctype = "application/octet-stream";
        }

        resp.setContentType(ctype);
        resp.setHeader("Cache-Control", "max-age=3600");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        java.io.OutputStream os = null;
        try {
            os = resp.getOutputStream();
            Files.copy(file, os);
            os.flush();
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception ignore) {}
            }
        }
    }

    /* ====================== DOTA objects ====================== */

    public AjaxResult getDotaObjects(Long datasetId, String imgName) {
        if (datasetId == null || StrUtil.isBlank(imgName)) return AjaxResult.error("参数缺失");

        OriginalDataset od = this.getById(datasetId);
        if (od == null) return AjaxResult.error("数据集不存在");

        try {
            Path imagesDir;
            if (StrUtil.isNotBlank(od.getDataPath())) {
                imagesDir = Paths.get(od.getDataPath()).normalize(); // .../{projectId}/images
            } else {
                imagesDir = DEFAULT_DATA_ROOT
                        .resolve(String.valueOf(od.getProjectId()))
                        .resolve("images")
                        .normalize();
            }

            Path rootDir = imagesDir.getParent(); // .../{projectId}
            if (rootDir == null) return AjaxResult.error("数据路径异常");

            Path annDir = rootDir.resolve("annotations").normalize();

            String safeName = normalizeImgParam(imgName);
            if (safeName == null) return AjaxResult.error("图片不存在");

            String baseName = safeName.replaceAll("\\.[^.]+$", "");

            Path imgFile = imagesDir.resolve(safeName).normalize();
            Path txtFile = annDir.resolve(baseName + ".txt").normalize();

            if (!imgFile.startsWith(imagesDir) || !Files.exists(imgFile)) {
                return AjaxResult.error("图片不存在");
            }

            if (!txtFile.startsWith(annDir) || !Files.exists(txtFile)) {
                Map<String, Object> payload = new LinkedHashMap<String, Object>();
                int[] wh = readImageWH(imgFile);
                payload.put("width", wh[0]);
                payload.put("height", wh[1]);
                payload.put("objects", Collections.emptyList());
                return AjaxResult.success(payload);
            }

            List<String> lines = Files.readAllLines(txtFile, StandardCharsets.UTF_8);
            List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>();

            for (int li = 0; li < lines.size(); li++) {
                String line = lines.get(li);
                if (StrUtil.isBlank(line)) continue;

                String[] parts = line.trim().split("\\s+");
                if (parts.length < 9) continue;

                double[] v = new double[8];
                try {
                    for (int i = 0; i < 8; i++) v[i] = Double.parseDouble(parts[i]);
                } catch (Exception e) {
                    continue;
                }

                String label = parts[8];
                int difficult = 0;
                if (parts.length >= 10) {
                    try { difficult = Integer.parseInt(parts[9]); } catch (Exception ignore) {}
                }

                List<List<Double>> pts = new ArrayList<List<Double>>(4);
                pts.add(Arrays.asList(v[0], v[1]));
                pts.add(Arrays.asList(v[2], v[3]));
                pts.add(Arrays.asList(v[4], v[5]));
                pts.add(Arrays.asList(v[6], v[7]));

                Map<String, Object> one = new LinkedHashMap<String, Object>();
                one.put("points", pts);
                one.put("label", label);
                one.put("difficult", difficult);
                objects.add(one);
            }

            int[] wh = readImageWH(imgFile);

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("width", wh[0]);
            payload.put("height", wh[1]);
            payload.put("objects", objects);
            return AjaxResult.success(payload);

        } catch (Exception e) {
            log.warn("getDotaObjects failed, datasetId={}, imgName={}", datasetId, imgName, e);
            return AjaxResult.error("读取标注失败");
        }
    }

    private int[] readImageWH(Path imgFile) {
        int w = 0, h = 0;
        try {
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(imgFile.toFile());
            if (bi != null) { w = bi.getWidth(); h = bi.getHeight(); }
        } catch (Exception ignore) {}
        return new int[]{w, h};
    }

    /* ====================== 私有工具 ====================== */

    private static class Aggregate {
        List<String> ids = new ArrayList<String>();
        List<String> names = new ArrayList<String>();
        int imgSum = 0;
        int annoSum = 0;
        Map<String, Long> classMap = new LinkedHashMap<String, Long>();
        int classCount = 0;
        Set<String> classKeys = new LinkedHashSet<String>();
    }

    private Aggregate aggregateFor(LinkedHashSet<Long> idSet, Map<Long, OriginalDataset> byId) {
        Aggregate a = new Aggregate();
        if (idSet == null || idSet.isEmpty()) return a;

        for (Long id : idSet) {
            OriginalDataset od = byId.get(id);
            if (od == null) continue;

            a.ids.add(String.valueOf(id));
            a.names.add(StrUtil.emptyToDefault(od.getName(), String.valueOf(id)));

            a.imgSum += (od.getImgNum() == null ? 0 : od.getImgNum());
            a.annoSum += (od.getAnnoNum() == null ? 0 : od.getAnnoNum());

            Map<String, Long> one = safeParseClassMap(od.getClassList());
            for (Map.Entry<String, Long> e : one.entrySet()) {
                String k = e.getKey();
                Long vv = e.getValue();
                long v = (vv == null ? 0L : vv.longValue());
                if (a.classMap.containsKey(k)) {
                    a.classMap.put(k, a.classMap.get(k) + v);
                } else {
                    a.classMap.put(k, v);
                }
            }
        }

        a.classCount = a.classMap.size();
        a.classKeys.addAll(a.classMap.keySet());
        return a;
    }

    private Map<String, Long> safeParseClassMap(String json) {
        Map<String, Long> map = new LinkedHashMap<String, Long>();
        if (StrUtil.isBlank(json)) return map;

        try {
            if (JSONUtil.isTypeJSONObject(json)) {
                JSONObject obj = JSONUtil.parseObj(json);
                for (String key : obj.keySet()) {
                    Object val = obj.get(key);
                    long n = 0L;
                    if (val instanceof Number) n = ((Number) val).longValue();
                    else if (val != null) {
                        try { n = Long.parseLong(String.valueOf(val)); } catch (Exception ignore) {}
                    }
                    map.put(key, n);
                }
            }
        } catch (Exception ignore) {
        }
        return map;
    }

    private String joinUnderScore(List<String> arr) {
        if (arr == null || arr.isEmpty()) return null;
        return String.join("_", arr);
    }

    private String pickSensorType(LinkedHashSet<Long> targetSet,
                                  LinkedHashSet<Long> trainSet,
                                  Map<Long, OriginalDataset> byId) {
        Iterable<Long> it = (targetSet != null && !targetSet.isEmpty()) ? targetSet : trainSet;
        if (it == null) return null;

        for (Long id : it) {
            OriginalDataset od = byId.get(id);
            if (od != null && StrUtil.isNotBlank(od.getSensorType())) {
                return od.getSensorType();
            }
        }
        return null;
    }

    private String deriveTargetType(Set<String> classKeys) {
        if (classKeys == null || classKeys.isEmpty()) return null;
        if (classKeys.size() >= 2) return "复合";

        String only = classKeys.iterator().next();
        String k = only == null ? "" : only.trim().toLowerCase(Locale.ROOT);

        if ("ship".equals(k)) return "舰船";
        if ("car".equals(k)) return "车辆";
        if ("plane".equals(k)) return "飞机";
        return "其他";
    }

    private List<String> pickImagesForLabel(Long projectId, List<Long> taskIds, String label, int limit) {
        if (projectId == null || StrUtil.isBlank(label)) return Collections.<String>emptyList();

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();

        sql.append("SELECT t.img_name ")
                .append("FROM ( ")
                .append("  SELECT ai.img_name, MAX(ai.created_time) AS latest ")
                .append("  FROM anno_info ai ")
                .append("  JOIN img_info ii ON ii.id = ai.image_id ")
                .append("  WHERE ii.project_id = ? AND ai.class_name = ? ");

        params.add(projectId);
        params.add(label);

        if (taskIds != null && !taskIds.isEmpty()) {
            String marks = taskIds.stream().map(x -> "?").collect(Collectors.joining(","));
            sql.append("  AND ii.task_id IN (").append(marks).append(") ");
            params.addAll(taskIds);
        }

        sql.append("  GROUP BY ai.img_name ")
                .append(") t ")
                .append("ORDER BY t.latest DESC ")
                .append("LIMIT ?");

        params.add(limit);

        try {
            return jdbcTemplate.queryForList(sql.toString(), params.toArray(), String.class);
        } catch (Exception e) {
            log.warn("pickImagesForLabel query fail, projectId={}, label={}", projectId, label, e);
            return Collections.<String>emptyList();
        }
    }

    private Set<String> parseClassKeys(String classJson) {
        Set<String> keys = new LinkedHashSet<String>();
        if (StrUtil.isBlank(classJson)) return keys;

        try {
            if (JSONUtil.isTypeJSONObject(classJson)) {
                JSONObject obj = JSONUtil.parseObj(classJson);
                keys.addAll(obj.keySet());
            }
        } catch (Exception ignore) {
        }
        return keys;
    }

    private List<Long> parseTaskIds(String taskIdsStr) {
        if (StrUtil.isBlank(taskIdsStr)) return Collections.<Long>emptyList();

        String[] arr = taskIdsStr.split("_");
        List<Long> out = new ArrayList<Long>(arr.length);

        for (int i = 0; i < arr.length; i++) {
            String s = arr[i];
            try {
                String t = s.trim();
                if (!t.isEmpty()) out.add(Long.parseLong(t));
            } catch (Exception ignore) {
            }
        }
        return out;
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private String ensureBaseUrl(String baseUrl) {
        String b = firstNonBlank(baseUrl, publicBaseUrl);
        if (StrUtil.isBlank(b)) b = "http://127.0.0.1:8081";

        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    private String firstNonBlank(String a, String b) {
        if (StrUtil.isNotBlank(a)) return a;
        if (StrUtil.isNotBlank(b)) return b;
        return null;
    }

    /**
     * ✅ Java8 风格：只在包含 '%' 时做一次 URLDecode，避免把 '+' 误解为空格
     * 同时去掉 images/ 前缀，并最终扁平化为仅文件名，匹配你当前落盘方式
     */
    private String normalizeImgParam(String imgName) {
        if (imgName == null) return null;

        String s = imgName;

        // 只在看起来像编码时 decode 一次（避免二次 decode / '+' -> 空格）
        if (s.indexOf('%') >= 0) {
            try {
                s = URLDecoder.decode(s, StandardCharsets.UTF_8.name());
            } catch (Exception ignore) {
            }
        }

        s = s.replace('\\', '/');

        // 去掉开头的 /
        while (s.startsWith("/")) s = s.substring(1);

        // 去掉常见前缀：images/
        if (s.startsWith("images/")) s = s.substring("images/".length());

        // 你当前落盘是“扁平化文件名”，取最后一段最保险
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);

        s = StrUtil.trimToNull(s);
        if (s == null) return null;

        // 简单防穿越（扁平化后一般不会再出现）
        if (s.contains("..")) return null;

        return s;
    }
}
