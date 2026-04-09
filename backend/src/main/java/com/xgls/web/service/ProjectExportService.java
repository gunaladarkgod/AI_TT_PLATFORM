package com.xgls.web.service;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.EngineProject;
import com.xgls.web.entity.User;
import com.xgls.web.utils.CvatApiUtil;
import com.xgls.web.utils.SessionUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectExportService {

    private final EngineProjectService engineProjectService;
    private final CvatXmlIngestService cvatXmlIngestService; // 解析 XML + 入库 + 生成 DOTA
    private final JdbcTemplate jdbcTemplate;                 // 写 original_dataset / 读 img_info

    @Value("${sys.root-upload}")
    private String rootPath;

    /** 可选：覆盖默认 original_dataset 根目录；不配则回退到 ${rootPath}/data/original_dataset */
    @Value("${sys.original-dataset-root:}")
    private String oriDatasetRoot;

    /** 防重入：同一 project 并发只跑一个（进程内） */
    private static final Set<Integer> RUNNING = new ConcurrentHashSet<>();

    /* ========= 英文→中文映射（大小写不敏感，含常见别名） ========= */
    private static final Map<String, String> SENSOR_MAP = new HashMap<>() {{
        put("nature", "自然");
        put("sar", "遥感SAR");
        put("vl", "遥感可见光");
        put("inf", "遥感红外");
        put("mul", "遥感多光谱");
    }};
    private static final Map<String, String> TARGET_MAP = new HashMap<>() {{
        put("ship", "舰船");
        put("mix", "复合");
        put("plane", "飞机");
        put("car", "车辆");
        put("other", "其他");
    }};

    private static String toSensorZh(String s) {
        if (StrUtil.isBlank(s)) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        if ("airsar".equals(k)) k = "sar"; // 兼容别名
        return SENSOR_MAP.getOrDefault(k, s);
    }

    private static String toTargetZh(String s) {
        if (StrUtil.isBlank(s)) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        return TARGET_MAP.getOrDefault(k, s);
    }
    /* ============================================================ */

    @Data
    public static class ResultItem {
        private Integer projectId;
        private String status; // success / fail / skip
        private String reason;
    }

    @Data
    public static class Result {
        private int success;
        private int fail;
        private int skip;
        private List<ResultItem> detail = new ArrayList<>();
    }

    /** 外部入口：扫描未落盘的项目，逐个执行【项目级导出】→ 落盘 → 解析入库 + 生成 DOTA → 汇总写 original_dataset */
    public Result exportMissingProjects(List<Integer> projectIds, boolean filterByUser) {
        Result ret = new Result();
        List<EngineProject> projects = listProjects(projectIds, filterByUser);

        for (EngineProject p : projects) {
            Integer pid = p.getId();
            if (pid == null) continue;

            if (!RUNNING.add(pid)) {
                add(ret, pid, "skip", "project is running");
                continue;
            }

            try {
                // 0) 先确认 CVAT 侧项目是否存在
                AjaxResult exists = CvatApiUtil.projectExists(pid);
                if (!exists.isSuccess()) {
                    add(ret, pid, "fail", "project not found");
                    continue;
                }

                Path baseDir = getProjectBaseDir(pid);
                Path imgDir = baseDir.resolve(CodeMap.DIR_IMAGES);
                Path annDir = baseDir.resolve(CodeMap.DIR_ANNOTATIONS);
                Path xmlPath = annDir.resolve("annotations.xml");

                boolean imagesReady = Files.exists(imgDir) && !isEmpty(imgDir);
                boolean annoXmlReady = Files.exists(annDir) && Files.exists(xmlPath);

                // 1) 若已经有 images + annotations.xml，则直接解析入库 + 生成 DOTA + 写 original_dataset
                if (imagesReady && annoXmlReady) {
                    try {
                        var summary = cvatXmlIngestService.ingest(pid, xmlPath, imgDir, annDir, true);
                        upsertOriginalDataset(pid, summary, imgDir, annDir, xmlPath);
                        log.info("project {} ingest (existing files) done, images={}, annos={}",
                                pid, summary.imageCount, summary.annoCount);
                        add(ret, pid, "success", null);
                    } catch (Exception e) {
                        log.error("ingest existing failed, project {}", pid, e);
                        add(ret, pid, "fail", "ingest xml failed");
                    }
                    continue;
                }

                // 2) 发起项目级导出
                boolean exported = doProjectExport(pid, true);
                if (!exported) {
                    add(ret, pid, "fail", "project export failed");
                    continue;
                }

                // 3) 将解压结果搬到标准目录
                boolean moved = moveExportResultToStandardDirs(pid);
                if (!moved) {
                    add(ret, pid, "fail", "move exported files failed");
                    continue;
                }

                // 4) 解析 XML + 批量写库 + 逐图生成 DOTA TXT（无标注也生成）→ 汇总写 original_dataset
                try {
                    var summary = cvatXmlIngestService.ingest(pid, xmlPath, imgDir, annDir, true);
                    upsertOriginalDataset(pid, summary, imgDir, annDir, xmlPath);
                    log.info("project {} ingest done, images={}, annos={}", pid, summary.imageCount, summary.annoCount);
                    add(ret, pid, "success", null);
                } catch (Exception e) {
                    log.error("ingest failed after export, project {}", pid, e);
                    add(ret, pid, "fail", "ingest xml failed");
                }

            } catch (Exception e) {
                log.error("export project {} error", pid, e);
                add(ret, pid, "fail", e.getMessage());
            } finally {
                RUNNING.remove(pid);
            }
        }
        return ret;
    }

    /* ==================== original_dataset 汇总写入（含中文映射&username） ==================== */

    private void upsertOriginalDataset(Integer projectId,
                                       CvatXmlIngestService.IngestResult summary,
                                       Path imgDir,
                                       Path annDir,
                                       Path xmlPath) {
        // 1) 项目名
        EngineProject proj = engineProjectService.getById(projectId);
        String projectName = (proj != null && StrUtil.isNotBlank(proj.getName())) ? proj.getName() : ("project_" + projectId);

        // 2) 解析 sensor/target/datasetName
        NameParts nameParts = parseNameParts(projectName);

        // 2.1 英文→中文（为空时返回 null）
        String sensorCn = toSensorZh(nameParts.sensor);
        String targetCn = toTargetZh(nameParts.target);

        // 3) 任务ID / 任务名（✅ 语义正确：优先从 CVAT Task 真名获取；失败再回退 XML，并过滤 default）
        String taskIdStr = joinTaskIds(projectId);
        String taskNameStr = joinTaskNamesFromCvat(projectId, taskIdStr);
        if (StrUtil.isBlank(taskNameStr)) {
            taskNameStr = joinTaskNamesFromXml(xmlPath, taskIdStr);
        }

        // 4) 类别统计 JSON
        String classListJson = new JSONObject(summary.classCounts).toString();

        // 5) 获取当前登录用户名（若无会是 null）
        User cur = SessionUtil.getCurUser();
        String username = (cur != null && StrUtil.isNotBlank(cur.getUsername())) ? cur.getUsername() : null;

        // 6) INSERT or UPDATE
        Long existId = queryOriginalDatasetId(projectId, 1);
        if (existId == null) {
            String sql = """
            INSERT INTO original_dataset
               (name, sensor_type, target_type,
                img_num, anno_num, class_num, class_list,
                data_format, username, data_path, anno_path,
                type_mark, project_id, project_name, task_id, task_name)
            VALUES (?,?,?,?,?,?,?, ?,?,?,?, ?,?,?,?,?)
            """;
            jdbcTemplate.update(sql,
                    // name/sensor/target（中文）
                    StrUtil.emptyToDefault(nameParts.datasetName, projectName),
                    emptyToNull(sensorCn),
                    emptyToNull(targetCn),
                    // counts
                    summary.imageCount,
                    summary.annoCount,
                    summary.classCounts.size(),
                    classListJson,
                    // format & paths & username
                    1, // CVAT
                    username,
                    imgDir.toString(),
                    annDir.toString(),
                    0, // 0-预训练
                    projectId,
                    projectName,
                    emptyToNull(taskIdStr),
                    emptyToNull(taskNameStr)
            );
        } else {
            String sql = """
            UPDATE original_dataset
               SET name=?,
                   sensor_type=?,
                   target_type=?,
                   img_num=?,
                   anno_num=?,
                   class_num=?,
                   class_list=?,
                   username=COALESCE(?, username),
                   data_path=?,
                   anno_path=?,
                   project_name=?,
                   task_id=?,
                   task_name=?,
                   updated_time=NOW()
             WHERE id=?
            """;
            jdbcTemplate.update(sql,
                    StrUtil.emptyToDefault(nameParts.datasetName, projectName),
                    emptyToNull(sensorCn),
                    emptyToNull(targetCn),
                    summary.imageCount,
                    summary.annoCount,
                    summary.classCounts.size(),
                    classListJson,
                    username,
                    imgDir.toString(),
                    annDir.toString(),
                    projectName,
                    emptyToNull(taskIdStr),
                    emptyToNull(taskNameStr),
                    existId
            );
        }
    }

    private static String emptyToNull(String s) {
        return StrUtil.isBlank(s) ? null : s;
    }

    private Long queryOriginalDatasetId(long projectId, int dataFormat) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM original_dataset WHERE project_id=? AND data_format=? LIMIT 1",
                (rs, i) -> rs.getLong(1),
                projectId, dataFormat
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static class NameParts {
        String sensor;
        String target;
        String datasetName;
    }

    /** 支持 "#5 sar_ship_ssdd" 或 "sar_ship_ssdd"；不足三段时尽力解析 */
    private NameParts parseNameParts(String projectName) {
        NameParts np = new NameParts();
        if (StrUtil.isBlank(projectName)) return np;

        String s = projectName.trim();
        if (s.startsWith("#")) {
            int sp = s.indexOf(' ');
            if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
        }
        String[] parts = s.split("[\\s]+", 2);
        s = parts.length == 2 ? parts[1] : parts[0];

        String[] seg = s.split("_");
        if (seg.length >= 1) np.sensor = seg[0] == null ? null : seg[0].toLowerCase(Locale.ROOT);
        if (seg.length >= 2) np.target = seg[1] == null ? null : seg[1].toLowerCase(Locale.ROOT);
        if (seg.length >= 3) {
            np.datasetName = String.join("_", Arrays.copyOfRange(seg, 2, seg.length));
        } else {
            np.datasetName = s;
        }
        return np;
    }

    /** 从 img_info 读出 distinct task_id 并用 "_" 连接 */
    private String joinTaskIds(long projectId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT DISTINCT task_id FROM img_info WHERE project_id=? AND task_id IS NOT NULL ORDER BY task_id",
                (rs, i) -> rs.getLong(1),
                projectId
        );
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining("_"));
    }

    /**
     * ✅ 优先从 CVAT 的 Task 列表取真实 name（语义正确）
     * getTasksByProject 返回 results: [{id,name,...}, ...]
     */
    private String joinTaskNamesFromCvat(long projectId, String taskIdStr) {
        if (StrUtil.isBlank(taskIdStr)) return null;
        try {
            var resp = CvatApiUtil.getTasksByProject((int) projectId);
            if (!resp.getStatusCode().is2xxSuccessful() || StrUtil.isBlank(resp.getBody())) return null;

            JSONObject root = JSONUtil.parseObj(resp.getBody());
            JSONArray results = root.getJSONArray("results");
            if (results == null || results.isEmpty()) return null;

            Map<String, String> id2name = new HashMap<>();
            for (int i = 0; i < results.size(); i++) {
                Object it = results.get(i);

                JSONObject obj;
                if (it instanceof JSONObject) {
                    obj = (JSONObject) it;
                } else {
                    // 可能是 Map / String 等，统一转 JSONObject
                    obj = JSONUtil.parseObj(it);
                }

                Integer id = obj.getInt("id");
                String name = obj.getStr("name");
                if (id != null && StrUtil.isNotBlank(name)) {
                    id2name.put(String.valueOf(id), name.trim());
                }
            }

            List<String> out = new ArrayList<>();
            for (String tid : taskIdStr.split("_")) {
                String k = tid.trim();
                if (k.isEmpty()) continue;
                out.add(id2name.getOrDefault(k, "task-" + k));
            }
            return out.isEmpty() ? null : String.join("_", out);
        } catch (Exception e) {
            log.warn("joinTaskNamesFromCvat failed, projectId={}", projectId, e);
            return null;
        }
    }


    /**
     * 兜底从 XML 里取每个 task_id 对应的 subset 作为 task_name；
     * ✅ subset 为空或 default 视为无效，直接跳过，让它回退到 task-{id}
     * 最终按 taskIdStr 的顺序拼接，用 "_" 连接
     */
    private String joinTaskNamesFromXml(Path xmlPath, String taskIdStr) {
        if (StrUtil.isBlank(taskIdStr) || xmlPath == null || !Files.exists(xmlPath)) return null;
        try {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlPath.toFile());
            doc.getDocumentElement().normalize();
            NodeList imgs = doc.getElementsByTagName("image");
            Map<String, String> id2name = new HashMap<>();

            for (int i = 0; i < imgs.getLength(); i++) {
                Element el = (Element) imgs.item(i);
                String tid = el.getAttribute("task_id");
                if (StrUtil.isBlank(tid)) continue;

                String subset = el.getAttribute("subset");
                if (StrUtil.isBlank(subset) || "default".equalsIgnoreCase(subset.trim())) {
                    continue;
                }
                id2name.putIfAbsent(tid, subset.trim());
            }

            List<String> out = new ArrayList<>();
            for (String tid : taskIdStr.split("_")) {
                String k = tid.trim();
                if (k.isEmpty()) continue;
                out.add(id2name.getOrDefault(k, "task-" + k));
            }
            return out.isEmpty() ? null : String.join("_", out);
        } catch (Exception e) {
            log.warn("parse task names from xml failed: {}", xmlPath, e);
            return null;
        }
    }

    /* ==================== 私有方法 ==================== */

    private List<EngineProject> listProjects(List<Integer> ids, boolean filterByUser) {
        LambdaQueryWrapper<EngineProject> w = new LambdaQueryWrapper<>();
        if (ids != null && !ids.isEmpty()) {
            w.in(EngineProject::getId, ids);
        }
        // 如需权限过滤，这里加 Session / UserProjectService 等
        return engineProjectService.list(w);
    }

    private Path getProjectBaseDir(Integer projectId) {
        String base = StrUtil.isNotBlank(oriDatasetRoot)
                ? oriDatasetRoot
                : Paths.get(rootPath, CodeMap.DIR_DATA, CodeMap.DIR_ORIGINAL_DATASET).toString();
        return Paths.get(base, projectId.toString());
    }

    private static boolean isEmpty(Path dir) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            return !ds.iterator().hasNext();
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 调用 CVAT 的【项目级导出】，轮询 rq_id，下载 zip 并解压到 temp/proj_{id}_unzipped
     */
    private boolean doProjectExport(Integer projectId, boolean includeImages) {
        try {
            AjaxResult res = CvatApiUtil.initProjectDatasetExport(projectId, CodeMap.DATASET_FORMAT, includeImages);
            if (!res.isSuccess()) {
                log.warn("init project export failed, pid:{}, ret: {}", projectId, res);
                return false;
            }
            JSONObject json = new JSONObject(String.valueOf(res.getData()));
            String rqId = json.getStr("rq_id");
            if (rqId == null) {
                log.warn("rq_id is null, projectId:{}", projectId);
                return false;
            }

            sleep(2);

            String downloadUrl = null;
            for (int i = 0; i < 600; i++) { // 最多等 ~50 分钟（5s * 600）
                AjaxResult q = CvatApiUtil.queryRequestStatus(rqId);
                if (!q.isSuccess()) {
                    log.warn("query status failed, pid:{}, ret: {}", projectId, q);
                    return false;
                }
                JSONObject st = new JSONObject(String.valueOf(q.getData()));
                String status = st.getStr("status");
                if ("failed".equalsIgnoreCase(status)) {
                    log.warn("project export failed, projectId:{}", projectId);
                    return false;
                } else if ("finished".equalsIgnoreCase(status)) {
                    downloadUrl = st.getStr("result_url");
                    break;
                } else {
                    sleep(5);
                }
            }
            if (downloadUrl == null) {
                log.warn("project export timeout, projectId:{}", projectId);
                return false;
            }

            Path tmpZip = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TEMP,
                    "proj_" + projectId + "_" + System.currentTimeMillis() + CodeMap.ZIP_SUFF);
            Files.createDirectories(tmpZip.getParent());
            FileUtil.del(tmpZip.toString()); // 确保不存在

            AjaxResult down = CvatApiUtil.downloadFile(downloadUrl, tmpZip.toString());
            if (!down.isSuccess()) {
                log.warn("download project zip failed, projectId:{}, msg:{}", projectId, down.getMsg());
                FileUtil.del(tmpZip.toString());
                return false;
            }

            // 解压到 temp 目录，后续再移动到标准目录
            Path unzipRoot = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TEMP,
                    "proj_" + projectId + "_unzipped");
            FileUtil.del(unzipRoot.toString());
            Files.createDirectories(unzipRoot);

            try (FileSystem zipfs = FileSystems.newFileSystem(tmpZip, (ClassLoader) null)) {
                for (Path rp : zipfs.getRootDirectories()) {
                    copyDirectoryRecursively(rp, unzipRoot); // 修复 ProviderMismatch
                }
            } finally {
                FileUtil.del(tmpZip.toString());
            }
            return true;
        } catch (Exception e) {
            log.error("doProjectExport error, projectId:{}", projectId, e);
            return false;
        }
    }

    /**
     * 将解压结果移动到标准目录：
     *   original_dataset/<projectId>/images
     *   original_dataset/<projectId>/annotations
     */
    private boolean moveExportResultToStandardDirs(Integer projectId) {
        try {
            Path unzipRoot = Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_TEMP,
                    "proj_" + projectId + "_unzipped");
            if (!Files.exists(unzipRoot)) {
                log.warn("unzip dir not exists for project {}", projectId);
                return false;
            }
            Path dstBase = getProjectBaseDir(projectId);
            Path dstImg = dstBase.resolve(CodeMap.DIR_IMAGES);
            Path dstAnno = dstBase.resolve(CodeMap.DIR_ANNOTATIONS);
            Files.createDirectories(dstImg);
            Files.createDirectories(dstAnno);

            // 1) 图片：优先匹配目录名包含 "image"，否则扫描所有常见图片
            List<Path> imageDirs = findDirsByNameLike(unzipRoot, "image");
            if (imageDirs.isEmpty()) imageDirs = List.of(unzipRoot);
            for (Path dir : imageDirs) {
                copyImagesRecursively(dir, dstImg);
            }

            // 2) 标注：优先 annotations.xml；其次 labels 目录；兜底 *.xml/*.json/*.txt
            Path annoXml = findFirstFileByName(unzipRoot, "annotations.xml");
            if (annoXml != null) {
                Files.copy(annoXml, dstAnno.resolve("annotations.xml"), StandardCopyOption.REPLACE_EXISTING);
            } else {
                List<Path> labelDirs = findDirsByNameLike(unzipRoot, "label");
                boolean hasYolo = false;
                for (Path dir : labelDirs) {
                    if (Files.isDirectory(dir)) {
                        copyDirectoryRecursively(dir, dstAnno.resolve("labels"));
                        hasYolo = true;
                        break;
                    }
                }
                if (!hasYolo) {
                    copyAnnoFilesRecursively(unzipRoot, dstAnno);
                }
            }

            // 清理解压目录
            FileUtil.del(unzipRoot.toString());
            return true;
        } catch (Exception e) {
            log.error("moveExportResultToStandardDirs error, projectId:{}", projectId, e);
            return false;
        }
    }

    /* ==================== 工具方法（全 Path 版本） ==================== */

    private static List<Path> findDirsByNameLike(Path root, String key) {
        try (Stream<Path> s = Files.walk(root)) {
            List<Path> list = s.filter(p ->
                            Files.isDirectory(p) &&
                                    p.getFileName() != null &&
                                    p.getFileName().toString().toLowerCase().contains(key.toLowerCase()))
                    .collect(Collectors.toList());
            // 深路径优先
            list.sort(Comparator.comparingInt(p -> p.toString().length()));
            Collections.reverse(list);
            return list;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static Path findFirstFileByName(Path root, String name) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p)
                            && p.getFileName() != null
                            && p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 递归复制目录（支持 ZipFS → 本地 FS），修复 ProviderMismatch：
     * 使用 target.resolve( source.relativize(p).toString() )
     */
    private static void copyDirectoryRecursively(Path source, Path target) {
        try (Stream<Path> s = Files.walk(source)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String relStr = source.relativize(p).toString();
                Path dest = relStr.isEmpty() ? target : target.resolve(relStr);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            log.warn("copyDirectoryRecursively failed: {} -> {}, {}", source, target, e.toString());
        }
    }

    private static void copyImagesRecursively(Path from, Path to) {
        final String[] exts = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p)) {
                    String fn = p.getFileName().toString().toLowerCase();
                    for (String ext : exts) {
                        if (fn.endsWith(ext)) {
                            Files.createDirectories(to);
                            // 若存在重名图片，会被覆盖；如需保留层级可改为 to.resolve(from.relativize(p).toString())
                            Files.copy(p, to.resolve(p.getFileName().toString()),
                                    StandardCopyOption.REPLACE_EXISTING);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("copyImagesRecursively failed: {} -> {}, {}", from, to, e.toString());
        }
    }

    private static void copyAnnoFilesRecursively(Path from, Path to) {
        final String[] exts = {".xml", ".json", ".txt", ".csv"};
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p)) {
                    String fn = p.getFileName().toString().toLowerCase();
                    for (String ext : exts) {
                        if (fn.endsWith(ext)) {
                            Files.createDirectories(to);
                            Files.copy(p, to.resolve(p.getFileName().toString()),
                                    StandardCopyOption.REPLACE_EXISTING);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("copyAnnoFilesRecursively failed: {} -> {}, {}", from, to, e.toString());
        }
    }

    private static void sleep(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException ignored) {
        }
    }

    private static void add(Result r, Integer pid, String status, String reason) {
        ResultItem it = new ResultItem();
        it.setProjectId(pid);
        it.setStatus(status);
        it.setReason(reason);
        r.getDetail().add(it);
        switch (status) {
            case "success" -> r.setSuccess(r.getSuccess() + 1);
            case "fail" -> r.setFail(r.getFail() + 1);
            default -> r.setSkip(r.getSkip() + 1);
        }
    }
}
