package com.xgls.web.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
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
import org.springframework.web.multipart.MultipartFile;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /** 原始数据集根目录（用于保存外部导入注册表） */
    @Value("${sys.original-dataset-root:/home/omen1/AI_TT_Platform/data/original_dataset}")
    private String originalDatasetRoot;

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

    /* ====================== 外部导入数据集（注册表） ====================== */

    public AjaxResult validateExternalDatasetPath(String rawPath) {
        ScanStat stat = scanExternalDataset(rawPath);
        if (!stat.valid) {
            return AjaxResult.error(stat.errorMsg);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("path", stat.datasetRoot.toString().replace("\\", "/"));
        data.put("suggestName", stat.datasetRoot.getFileName() != null ? stat.datasetRoot.getFileName().toString() : "external_dataset");
        data.put("imgNum", stat.imageCount);
        data.put("annoNum", stat.boxCount);
        data.put("classNum", stat.classMap.size());
        data.put("classList", JSONUtil.toJsonStr(stat.classMap));
        return AjaxResult.success(data);
    }

    public AjaxResult pickLocalDirectory() {
        // 优先走 Linux 桌面选择器（同机部署最直观）
        // 兼容“后端进程未继承 DISPLAY”场景：自动探测 :1 / :0 等
        List<String> displays = new ArrayList<String>();
        String envDisplay = System.getenv("DISPLAY");
        if (StrUtil.isNotBlank(envDisplay)) {
            displays.add(envDisplay.trim());
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get("/tmp/.X11-unix"), "X*")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                if (n.length() > 1) {
                    String d = ":" + n.substring(1);
                    if (!displays.contains(d)) displays.add(d);
                }
            }
        } catch (Exception ignore) {
        }
        if (!displays.contains(":1")) displays.add(":1");
        if (!displays.contains(":0")) displays.add(":0");

        String xauth = firstNonBlank(System.getenv("XAUTHORITY"), System.getProperty("user.home") + "/.Xauthority");
        String lastErr = null;
        for (String d : displays) {
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-lc", "zenity --file-selection --directory");
                pb.environment().put("DISPLAY", d);
                if (StrUtil.isNotBlank(xauth)) {
                    pb.environment().put("XAUTHORITY", xauth);
                }
                Process p = pb.start();
                int code = p.waitFor();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (code == 0 && StrUtil.isNotBlank(out)) {
                    Map<String, Object> data = new LinkedHashMap<String, Object>();
                    data.put("path", out.replace("\\", "/"));
                    data.put("display", d);
                    return AjaxResult.success(data);
                }
                if (StrUtil.isNotBlank(err)) {
                    lastErr = "DISPLAY=" + d + " -> " + err;
                }
            } catch (Exception e) {
                lastErr = "DISPLAY=" + d + " -> " + e.getMessage();
            }
        }

        if (GraphicsEnvironment.isHeadless()) {
            return AjaxResult.error("当前环境无图形界面，无法打开本机目录选择器"
                    + (StrUtil.isNotBlank(lastErr) ? ("；" + lastErr) : ""));
        }

        final String[] selected = new String[1];
        final String[] err = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("选择外来数据集目录");
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setAcceptAllFileFilterUsed(false);
                    int ret = chooser.showOpenDialog(null);
                    if (ret == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                        selected[0] = chooser.getSelectedFile().getAbsolutePath();
                    }
                } catch (Exception e) {
                    err[0] = e.getMessage();
                }
            });
        } catch (Exception e) {
            err[0] = e.getMessage();
        }

        if (StrUtil.isNotBlank(err[0])) {
            return AjaxResult.error("打开目录选择器失败: " + err[0]);
        }
        if (StrUtil.isBlank(selected[0])) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("path", "");
            data.put("cancelled", true);
            data.put("msg", "已取消选择");
            return AjaxResult.success(data);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("path", selected[0].replace("\\", "/"));
        data.put("cancelled", false);
        return AjaxResult.success(data);
    }

    public AjaxResult importExternalDataset(String name, String rawPath) {
        String dsName = StrUtil.trimToEmpty(name);
        if (StrUtil.isBlank(dsName)) {
            return AjaxResult.error("数据集显示名称不能为空");
        }
        ScanStat stat = scanExternalDataset(rawPath);
        if (!stat.valid) {
            return AjaxResult.error(stat.errorMsg);
        }

        try {
            List<RegistryItem> items = readExternalRegistry();
            for (RegistryItem it : items) {
                if (dsName.equals(it.name)) {
                    return AjaxResult.error("名称已存在，请更换后再导入");
                }
            }
            RegistryItem n = new RegistryItem();
            n.name = dsName;
            n.path = stat.datasetRoot.toString().replace("\\", "/");
            n.createdTime = LocalDateTime.now().toString();
            items.add(n);
            writeExternalRegistry(items);
            return AjaxResult.success("导入记录已保存");
        } catch (Exception e) {
            log.warn("importExternalDataset failed, name={}, path={}", dsName, rawPath, e);
            return AjaxResult.error("写入导入记录失败: " + e.getMessage());
        }
    }

    public AjaxResult deleteExternalDatasetRecord(String name, String path) {
        String n = StrUtil.trimToEmpty(name);
        String p = StrUtil.trimToEmpty(path);
        if (StrUtil.isBlank(n) || StrUtil.isBlank(p)) {
            return AjaxResult.error("缺少删除参数");
        }
        try {
            List<RegistryItem> items = readExternalRegistry();
            int before = items.size();
            items.removeIf(it -> n.equals(it.name) && p.equals(it.path));
            if (items.size() == before) {
                return AjaxResult.error("未找到对应导入记录");
            }
            writeExternalRegistry(items);
            return AjaxResult.success("删除成功");
        } catch (Exception e) {
            return AjaxResult.error("删除失败: " + e.getMessage());
        }
    }

    public AjaxResult importExternalDatasetByUpload(String name, List<MultipartFile> files, List<String> relPaths) {
        String dsName = StrUtil.trimToEmpty(name);
        if (StrUtil.isBlank(dsName)) {
            return AjaxResult.error("数据集显示名称不能为空");
        }
        if (files == null || files.isEmpty()) {
            return AjaxResult.error("未选择任何文件");
        }
        Path uploadedRoot = null;
        try {
            Path base = Paths.get(firstNonBlank(originalDatasetRoot, DEFAULT_DATA_ROOT.toString()))
                    .resolve("external_uploaded")
                    .resolve(System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", ""))
                    .normalize();
            Files.createDirectories(base);

            String firstSegment = null;
            for (int i = 0; i < files.size(); i++) {
                MultipartFile mf = files.get(i);
                if (mf == null || mf.isEmpty()) {
                    continue;
                }
                String rel = normalizeRelativeUploadPath(relPaths, i, mf.getOriginalFilename());
                if (StrUtil.isBlank(rel)) {
                    continue;
                }
                if (firstSegment == null) {
                    int k = rel.indexOf('/');
                    firstSegment = k > 0 ? rel.substring(0, k) : rel;
                }
                Path target = base.resolve(rel).normalize();
                if (!target.startsWith(base)) {
                    return AjaxResult.error("非法相对路径: " + rel);
                }
                Files.createDirectories(target.getParent());
                mf.transferTo(target);
            }
            if (StrUtil.isBlank(firstSegment)) {
                return AjaxResult.error("未解析到有效目录结构");
            }
            uploadedRoot = base.resolve(firstSegment).normalize();
            if (!Files.isDirectory(uploadedRoot)) {
                return AjaxResult.error("上传后目录结构异常");
            }
            return importExternalDataset(dsName, uploadedRoot.toString());
        } catch (Exception e) {
            log.warn("importExternalDatasetByUpload failed, name={}", dsName, e);
            return AjaxResult.error("上传导入失败: " + e.getMessage());
        }
    }

    /**
     * 按数据集名称解析「原始标签 → 样本数」（优先库表 class_list，否则外部注册路径扫描），供任务导出汇总目标类别数量。
     */
    public Map<String, Long> resolveClassCountMapByDatasetName(String datasetName) {
        String name = StrUtil.trimToEmpty(datasetName);
        if (StrUtil.isBlank(name)) {
            return Collections.emptyMap();
        }
        Map<String, Long> out = new LinkedHashMap<>();
        List<OriginalDataset> all = list(new LambdaQueryWrapper<OriginalDataset>().orderByDesc(OriginalDataset::getId));
        OriginalDataset latest = null;
        for (OriginalDataset od : all) {
            if (!name.equals(StrUtil.trimToEmpty(od.getName()))) {
                continue;
            }
            if (latest == null) {
                latest = od;
                continue;
            }
            long cur = od.getId() == null ? -1L : od.getId();
            long old = latest.getId() == null ? -1L : latest.getId();
            if (cur > old) {
                latest = od;
            }
        }
        if (latest != null && StrUtil.isNotBlank(latest.getClassList())) {
            try {
                JSONObject o = JSONUtil.parseObj(latest.getClassList());
                for (String k : o.keySet()) {
                    Object raw = o.get(k);
                    long v = 0L;
                    if (raw instanceof Number) {
                        v = ((Number) raw).longValue();
                    }
                    out.put(k, v);
                }
            } catch (Exception e) {
                log.debug("resolveClassCountMapByDatasetName parse class_list failed: {}", e.getMessage());
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        List<RegistryItem> reg = readExternalRegistry();
        for (RegistryItem it : reg) {
            if (!name.equals(StrUtil.trimToEmpty(it.name))) {
                continue;
            }
            ScanStat stat = scanExternalDataset(it.path);
            if (stat.valid && stat.classMap != null) {
                for (Map.Entry<String, Integer> e : stat.classMap.entrySet()) {
                    out.put(e.getKey(), e.getValue().longValue());
                }
            }
            break;
        }
        return out;
    }

    public List<Map<String, Object>> listExternalDatasets() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        List<RegistryItem> items = readExternalRegistry();
        for (int i = 0; i < items.size(); i++) {
            RegistryItem it = items.get(i);
            ScanStat stat = scanExternalDataset(it.path);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", "ext-" + i + "-" + Math.abs(Objects.hash(it.name, it.path)));
            row.put("name", it.name);
            row.put("sensor_type", "外部");
            row.put("target_type", "外部导入");
            row.put("username", "manual");
            row.put("data_source", "外部导入");
            row.put("is_external", true);
            row.put("external_path", it.path);
            if (stat.valid) {
                row.put("img_num", stat.imageCount);
                row.put("anno_num", stat.boxCount);
                row.put("class_num", stat.classMap.size());
                row.put("class_list", JSONUtil.toJsonStr(stat.classMap));
            } else {
                row.put("img_num", 0);
                row.put("anno_num", 0);
                row.put("class_num", 0);
                row.put("class_list", "{}");
                row.put("error", stat.errorMsg);
            }
            out.add(row);
        }
        return out;
    }

    public AjaxResult browseExternalDirs(String base) {
        Path root = resolveBrowseRoot();
        Path cur = StrUtil.isBlank(base) ? root : Paths.get(base).normalize();
        if (!cur.startsWith(root)) {
            return AjaxResult.error("非法路径：超出可浏览范围");
        }
        if (!Files.isDirectory(cur)) {
            return AjaxResult.error("目录不存在");
        }
        try {
            List<String> dirs = new ArrayList<String>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(cur)) {
                for (Path p : ds) {
                    if (Files.isDirectory(p)) {
                        dirs.add(p.getFileName().toString());
                    }
                }
            }
            dirs.sort(String::compareToIgnoreCase);
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("root", root.toString().replace("\\", "/"));
            data.put("base", cur.toString().replace("\\", "/"));
            Path parent = cur.getParent();
            data.put("parent", (parent != null && parent.startsWith(root)) ? parent.toString().replace("\\", "/") : null);
            data.put("dirs", dirs);
            return AjaxResult.success(data);
        } catch (Exception e) {
            return AjaxResult.error("读取目录失败: " + e.getMessage());
        }
    }

    public AjaxResult randomSampleImages(Long datasetId,
                                         boolean isExternal,
                                         String externalPath,
                                         List<String> exclude,
                                         int size,
                                         String baseUrl) {
        int n = size <= 0 ? 3 : Math.min(size, 10);
        Path imagesDir;
        String extPathNormalized = null;
        if (isExternal) {
            String p = StrUtil.trimToEmpty(externalPath);
            if (StrUtil.isBlank(p)) {
                return AjaxResult.error("外部数据集缺少路径");
            }
            Path root = Paths.get(p).normalize();
            imagesDir = resolveImagesDir(root);
            extPathNormalized = root.toString().replace("\\", "/");
            if (imagesDir == null) {
                return AjaxResult.error("外部数据集下未找到可用图片目录（应为数据集根目录或 images 目录）");
            }
        } else {
            if (datasetId == null) {
                return AjaxResult.error("缺少数据集ID");
            }
            OriginalDataset od = this.getById(datasetId);
            if (od == null) return AjaxResult.error("数据集不存在");
            if (StrUtil.isNotBlank(od.getDataPath())) {
                imagesDir = resolveImagesDir(Paths.get(od.getDataPath()).normalize());
            } else {
                imagesDir = resolveImagesDir(DEFAULT_DATA_ROOT.resolve(String.valueOf(od.getProjectId())).normalize());
            }
            if (imagesDir == null) {
                return AjaxResult.error("数据集图片目录不存在");
            }
        }

        List<Path> allImgs;
        try (Stream<Path> s = Files.walk(imagesDir)) {
            allImgs = s.filter(Files::isRegularFile).filter(this::isImageFile).collect(Collectors.toList());
        } catch (Exception e) {
            return AjaxResult.error("读取图片失败: " + e.getMessage());
        }
        if (allImgs.isEmpty()) {
            return AjaxResult.error("该数据集下没有可预览图片");
        }

        Set<String> excludes = new HashSet<>();
        if (exclude != null) {
            excludes.addAll(exclude.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()));
        }
        List<Path> candidate = new ArrayList<>();
        for (Path p : allImgs) {
            String key = toRelUnix(imagesDir, p);
            if (!excludes.contains(key)) {
                candidate.add(p);
            }
        }
        if (candidate.size() < n) {
            candidate = new ArrayList<>(allImgs);
        }
        Collections.shuffle(candidate);
        List<Path> picked = candidate.subList(0, Math.min(n, candidate.size()));

        String base = ensureBaseUrl(baseUrl);
        List<String> images = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Path p : picked) {
            String key = toRelUnix(imagesDir, p);
            keys.add(key);
            if (isExternal) {
                images.add(base + "/original-dataset/external/image?path="
                        + urlEncode(extPathNormalized) + "&img=" + urlEncode(key));
            } else {
                images.add(base + "/original-dataset/" + datasetId + "/image?img=" + urlEncode(key));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("images", images);
        data.put("keys", keys);
        data.put("count", images.size());
        return AjaxResult.success(data);
    }

    public void streamExternalImage(String datasetPath, String relImgPath,
                                    jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
        if (StrUtil.isBlank(datasetPath) || StrUtil.isBlank(relImgPath)) {
            resp.setStatus(404);
            return;
        }
        Path root = Paths.get(datasetPath).normalize();
        Path imagesDir = resolveImagesDir(root);
        if (imagesDir == null) {
            resp.setStatus(404);
            return;
        }
        String rel = relImgPath.replace("\\", "/");
        while (rel.startsWith("/")) rel = rel.substring(1);
        if (rel.contains("..")) {
            resp.setStatus(404);
            return;
        }
        Path file = imagesDir.resolve(rel).normalize();
        if (!file.startsWith(imagesDir) || !Files.exists(file) || Files.isDirectory(file)) {
            resp.setStatus(404);
            return;
        }
        String ctype = Files.probeContentType(file);
        if (StrUtil.isBlank(ctype)) {
            ctype = "application/octet-stream";
        }
        resp.setContentType(ctype);
        resp.setHeader("Cache-Control", "max-age=3600");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        try (java.io.OutputStream os = resp.getOutputStream()) {
            Files.copy(file, os);
            os.flush();
        }
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
            base = resolveImagesDir(Paths.get(od.getDataPath()).normalize());
        } else {
            base = resolveImagesDir(DEFAULT_DATA_ROOT.resolve(String.valueOf(od.getProjectId())).normalize());
        }
        if (base == null) {
            resp.setStatus(404);
            return;
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

    private static class RegistryItem {
        String name;
        String path;
        String createdTime;
    }

    private static class ScanStat {
        boolean valid;
        String errorMsg;
        Path datasetRoot;
        int imageCount;
        int boxCount;
        Map<String, Integer> classMap = new LinkedHashMap<String, Integer>();
    }

    private Path externalRegistryPath() {
        String root = firstNonBlank(originalDatasetRoot, DEFAULT_DATA_ROOT.toString());
        Path base = Paths.get(root).normalize();
        try {
            Files.createDirectories(base);
        } catch (Exception e) {
            log.warn("create original dataset root failed: {}", base, e);
        }
        return base.resolve("external_dataset_registry.json");
    }

    private List<RegistryItem> readExternalRegistry() {
        List<RegistryItem> out = new ArrayList<RegistryItem>();
        Path file = externalRegistryPath();
        try {
            if (!Files.exists(file)) {
                return out;
            }
            String txt = Files.readString(file, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(txt)) {
                return out;
            }
            JSONObject obj = JSONUtil.parseObj(txt);
            JSONArray arr = obj.getJSONArray("datasets");
            if (arr == null) {
                return out;
            }
            for (Object item : arr) {
                if (!(item instanceof JSONObject)) {
                    continue;
                }
                JSONObject one = (JSONObject) item;
                String name = StrUtil.trimToEmpty(one.getStr("name"));
                String path = StrUtil.trimToEmpty(one.getStr("path"));
                if (StrUtil.isBlank(name) || StrUtil.isBlank(path)) {
                    continue;
                }
                RegistryItem ri = new RegistryItem();
                ri.name = name;
                ri.path = path;
                ri.createdTime = one.getStr("createdTime");
                out.add(ri);
            }
        } catch (Exception e) {
            log.warn("read external registry failed: {}", file, e);
        }
        return out;
    }

    private void writeExternalRegistry(List<RegistryItem> items) throws Exception {
        Path file = externalRegistryPath();
        JSONObject obj = new JSONObject();
        JSONArray arr = new JSONArray();
        for (RegistryItem it : items) {
            JSONObject one = new JSONObject();
            one.put("name", it.name);
            one.put("path", it.path);
            one.put("createdTime", it.createdTime);
            arr.add(one);
        }
        obj.put("datasets", arr);
        Files.writeString(file, JSONUtil.toJsonPrettyStr(obj), StandardCharsets.UTF_8);
    }

    private ScanStat scanExternalDataset(String rawPath) {
        ScanStat stat = new ScanStat();
        String p = StrUtil.trimToEmpty(rawPath);
        if (StrUtil.isBlank(p)) {
            stat.valid = false;
            stat.errorMsg = "路径不能为空";
            return stat;
        }
        Path root = Paths.get(p).normalize();
        stat.datasetRoot = root;
        if (!Files.isDirectory(root)) {
            stat.valid = false;
            stat.errorMsg = "路径无效：目录不存在";
            return stat;
        }

        Path imagesDir = root.resolve("images").normalize();
        if (!Files.isDirectory(imagesDir)) {
            stat.valid = false;
            stat.errorMsg = "路径无效：缺少 images 目录";
            return stat;
        }

        Path annDir = root.resolve("annotations").normalize();
        if (!Files.isDirectory(annDir)) {
            stat.valid = false;
            stat.errorMsg = "路径无效：缺少 annotations 目录";
            return stat;
        }

        try {
            long imgCnt;
            try (java.util.stream.Stream<Path> s = Files.walk(imagesDir)) {
                imgCnt = s.filter(Files::isRegularFile).filter(this::isImageFile).count();
            }
            stat.imageCount = (int) Math.min(Integer.MAX_VALUE, imgCnt);

            int boxCnt = 0;
            Map<Integer, String> cocoCateMap = new LinkedHashMap<Integer, String>();
            try (java.util.stream.Stream<Path> s = Files.walk(annDir)) {
                List<Path> files = s.filter(Files::isRegularFile).collect(Collectors.toList());
                List<Path> txtFiles = files.stream()
                        .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .collect(Collectors.toList());
                for (Path f : txtFiles) {
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        if (StrUtil.isBlank(line)) {
                            continue;
                        }
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length < 9) {
                            continue;
                        }
                        String cls = parts[8].trim();
                        if (StrUtil.isBlank(cls)) {
                            continue;
                        }
                        boxCnt++;
                        stat.classMap.put(cls, stat.classMap.getOrDefault(cls, 0) + 1);
                    }
                }

                if (stat.classMap.isEmpty()) {
                    List<Path> jsonFiles = files.stream()
                            .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                            .collect(Collectors.toList());
                    for (Path jf : jsonFiles) {
                        parseCocoJsonStat(jf, stat.classMap, cocoCateMap);
                    }
                    for (Map.Entry<String, Integer> e : stat.classMap.entrySet()) {
                        boxCnt += Math.max(0, e.getValue());
                    }
                }
            }
            stat.boxCount = boxCnt;
            if (stat.imageCount <= 0) {
                stat.valid = false;
                stat.errorMsg = "路径无效：images 目录下未发现图片";
                return stat;
            }
            if (stat.classMap.isEmpty()) {
                stat.valid = false;
                stat.errorMsg = "路径无效：未发现可解析的标注类别";
                return stat;
            }
            stat.valid = true;
            return stat;
        } catch (Exception e) {
            stat.valid = false;
            stat.errorMsg = "读取数据集失败: " + e.getMessage();
            return stat;
        }
    }

    private boolean isImageFile(Path f) {
        String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".bmp") || n.endsWith(".tif") || n.endsWith(".tiff") || n.endsWith(".webp");
    }

    private String toRelUnix(Path base, Path file) {
        return base.relativize(file).toString().replace("\\", "/");
    }

    private String normalizeRelativeUploadPath(List<String> relPaths, int idx, String fallbackFileName) {
        String rel = null;
        if (relPaths != null && idx < relPaths.size()) {
            rel = relPaths.get(idx);
        }
        if (StrUtil.isBlank(rel)) {
            rel = fallbackFileName;
        }
        rel = StrUtil.trimToEmpty(rel).replace("\\", "/");
        while (rel.startsWith("/")) rel = rel.substring(1);
        if (rel.contains("..")) {
            return null;
        }
        return rel;
    }

    private Path resolveBrowseRoot() {
        String root = firstNonBlank(originalDatasetRoot, DEFAULT_DATA_ROOT.toString());
        Path p = Paths.get(root).normalize();
        Path parent = p.getParent();
        return parent != null ? parent : p;
    }

    private void parseCocoJsonStat(Path jsonFile, Map<String, Integer> classMap, Map<Integer, String> cateMap) {
        try {
            String txt = Files.readString(jsonFile, StandardCharsets.UTF_8);
            JSONObject obj = JSONUtil.parseObj(txt);
            JSONArray categories = obj.getJSONArray("categories");
            if (categories != null) {
                for (Object c : categories) {
                    if (!(c instanceof JSONObject)) {
                        continue;
                    }
                    JSONObject cj = (JSONObject) c;
                    Integer id = cj.getInt("id");
                    String name = StrUtil.trimToEmpty(cj.getStr("name"));
                    if (id != null && StrUtil.isNotBlank(name)) {
                        cateMap.put(id, name);
                        classMap.putIfAbsent(name, 0);
                    }
                }
            }
            JSONArray anns = obj.getJSONArray("annotations");
            if (anns != null && !cateMap.isEmpty()) {
                for (Object a : anns) {
                    if (!(a instanceof JSONObject)) {
                        continue;
                    }
                    JSONObject aj = (JSONObject) a;
                    Integer cid = aj.getInt("category_id");
                    String cn = cid != null ? cateMap.get(cid) : null;
                    if (StrUtil.isBlank(cn)) {
                        continue;
                    }
                    classMap.put(cn, classMap.getOrDefault(cn, 0) + 1);
                }
            }
        } catch (Exception e) {
            // ignore this json and keep scanning others
        }
    }

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

        s = StrUtil.trimToNull(s);
        if (s == null) return null;

        // 简单防穿越（扁平化后一般不会再出现）
        if (s.contains("..")) return null;

        return s;
    }

    /**
     * 兼容两种输入：
     * 1) 数据集根目录（其下包含 images 子目录）
     * 2) images 目录本身
     */
    private Path resolveImagesDir(Path candidate) {
        if (candidate == null || !Files.isDirectory(candidate)) return null;
        Path byChild = candidate.resolve("images").normalize();
        if (Files.isDirectory(byChild)) return byChild;
        return candidate;
    }
}
