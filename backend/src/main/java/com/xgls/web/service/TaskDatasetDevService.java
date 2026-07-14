package com.xgls.web.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.entity.User;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TaskDatasetDevService {

    @Value("${sys.original-dataset-root:data/original_dataset}")
    private String originalDatasetRoot;
    private final TaskDataset1Service taskDatasetService;
    private final OriginalDataset1Service originalDatasetService;
    private final InstanceDatasetMidService instanceDatasetMidService;
    private final JdbcTemplate jdbcTemplate;

    public TaskDatasetDevService(
            TaskDataset1Service taskDatasetService,
            OriginalDataset1Service originalDatasetService,
            InstanceDatasetMidService instanceDatasetMidService,
            JdbcTemplate jdbcTemplate) {
        this.taskDatasetService = taskDatasetService;
        this.originalDatasetService = originalDatasetService;
        this.instanceDatasetMidService = instanceDatasetMidService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public AjaxResult listTasks() {
        try {
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("读取任务定义失败: " + e.getMessage());
        }
    }

    public AjaxResult createTask(Map<String, Object> req) {
        String name = trim(req.get("name"));
        String desc = trim(req.get("desc"));
        List<String> targetSchema = toStringList(req.get("targetSchema"));
        List<String> testDatasets = toStringList(req.get("testDatasets"));

        if (StrUtil.isBlank(name)) return AjaxResult.error("请输入任务名称");
        if (targetSchema.isEmpty()) return AjaxResult.error("请至少填写一个目标类别");
        if (testDatasets.isEmpty()) return AjaxResult.error("请至少选择一个测试数据集");

        try {
            JSONObject root = readTaskRoot();
            if (root.containsKey(name)) {
                return AjaxResult.error("任务名称已存在，请更换后再创建");
            }

            JSONObject one = new JSONObject();
            one.set("desc", desc);
            one.set("target_schema", targetSchema);
            one.set("test_datasets", testDatasets);
            one.set("mapping_rules", new JSONObject());
            one.set("updated_time", LocalDateTime.now().toString());
            String username = currentUsername();
            if (StrUtil.isNotBlank(username)) {
                one.set("updated_by", username);
            }

            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("创建任务失败: " + e.getMessage());
        }
    }

    public AjaxResult deleteTask(Map<String, Object> req) {
        String taskName = trim(req != null ? req.get("name") : null);
        if (StrUtil.isBlank(taskName)) return AjaxResult.error("缺少任务名称");
        boolean alsoDeleteLocal = true;
        if (req != null && req.containsKey("alsoDeleteLocal")) {
            alsoDeleteLocal = Boolean.TRUE.equals(req.get("alsoDeleteLocal"));
        }
        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(taskName)) {
                return AjaxResult.error("任务不存在");
            }
            if (alsoDeleteLocal) {
                clearExportedMidByTask(taskName);
            }
            root.remove(taskName);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("删除任务失败: " + e.getMessage());
        }
    }

    public AjaxResult updateTask(Map<String, Object> req) {
        String originalName = trim(req.get("originalName"));
        String name = trim(req.get("name"));
        String desc = trim(req.get("desc"));
        List<String> targetSchema = toStringList(req.get("targetSchema"));
        List<String> testDatasets = toStringList(req.get("testDatasets"));

        if (StrUtil.isBlank(originalName)) return AjaxResult.error("缺少原任务名称");
        if (StrUtil.isBlank(name)) return AjaxResult.error("请输入任务名称");
        if (targetSchema.isEmpty()) return AjaxResult.error("请至少填写一个目标类别");
        if (testDatasets.isEmpty()) return AjaxResult.error("请至少选择一个测试数据集");

        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(originalName)) {
                return AjaxResult.error("任务不存在");
            }
            if (!StrUtil.equals(originalName, name) && root.containsKey(name)) {
                return AjaxResult.error("任务名称已存在，请更换后再保存");
            }

            JSONObject oldTask = root.getJSONObject(originalName);
            JSONObject one = new JSONObject();
            one.set("desc", desc);
            one.set("target_schema", targetSchema);
            one.set("test_datasets", testDatasets);
            one.set("mapping_rules", oldTask != null ? oldTask.getJSONObject("mapping_rules") : new JSONObject());
            if (oldTask != null) {
                carryExportMeta(oldTask, one);
            }
            one.set("updated_time", LocalDateTime.now().toString());
            String username = currentUsername();
            if (StrUtil.isNotBlank(username)) {
                one.set("updated_by", username);
            }

            root.remove(originalName);
            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("更新任务失败: " + e.getMessage());
        }
    }

    public AjaxResult exportTask(Map<String, Object> req) {
        String name = trim(req.get("name"));
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            JSONObject root = readTaskRoot();
            JSONObject one = root.getJSONObject(name);
            if (one == null) return AjaxResult.error("任务不存在");

            MappingStatus mappingStatus = assessMappingStatus(one);
            if (!mappingStatus.ok) {
                return AjaxResult.error("导出前检查失败（映射不完整）：" + mappingStatus.detail);
            }

            List<String> datasetNames = jsonArrayToList(one.getJSONArray("test_datasets"));
            List<DatasetSource> sources = resolveDatasetSources(datasetNames);
            if (!sources.isEmpty()) {
                int exported = exportFromDatasetSources(name, one, sources);
                if (exported <= 0) {
                    return AjaxResult.error("导出失败：没有找到包含已映射类别的图片和标注，请检查映射关系及 images/annotations 内容。");
                }
            } else {
                return AjaxResult.error("导出失败：无法读取所选原始数据集目录。请在原始数据集管理中重新导入或修正路径后再导出；已停止使用会生成 train/test 且无法严格应用映射的旧导出方式。");
            }

            one.set("last_export_time", LocalDateTime.now().toString());
            one.set("last_export_source_updated_time", one.getStr("updated_time", ""));
            one.set("last_export_by", currentUsername());
            one.set("last_export_mid_count", countMidByFatherName(name));
            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("导出失败: " + e.getMessage());
        }
    }

    public AjaxResult clearTask(Map<String, Object> req) {
        String name = trim(req != null ? req.get("name") : null);
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            if (!hasExportedMidArtifacts(name)) {
                return AjaxResult.error("无需清除：当前没有已导出的中间数据集（数据库与目录均未发现对应内容）");
            }
            clearExportedMidByTask(name);
            JSONObject root = readTaskRoot();
            JSONObject one = root.getJSONObject(name);
            if (one != null) {
                // 移除键（比设空串更可靠，避免序列化/反序列化后仍被当成已导出）
                one.remove("last_export_time");
                one.remove("last_export_source_updated_time");
                one.remove("last_export_by");
                one.set("last_export_mid_count", 0);
                root.set(name, one);
                writeTaskRoot(root);
            }
            // MyBatis updateById 默认忽略 null 字段，必须显式 SET NULL，否则清除后仍会被判定为已导出。
            taskDatasetService.lambdaUpdate()
                    .eq(TaskDataset::getName, name)
                    .set(TaskDataset::getLastExportTime, null)
                    .set(TaskDataset::getLastExportSourceUpdatedTime, null)
                    .set(TaskDataset::getLastExportBy, null)
                    .set(TaskDataset::getLastExportMidCount, 0)
                    .update();
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("清除失败: " + e.getMessage());
        }
    }

    public AjaxResult updateMappingRules(Map<String, Object> req) {
        String name = trim(req.get("name"));
        Object mappingRules = req.get("mappingRules");
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(name)) {
                return AjaxResult.error("任务不存在");
            }
            JSONObject one = root.getJSONObject(name);
            JSONObject normalized = normalizeMappingRules(mappingRules);
            JSONObject previous = normalizeMappingRules(one.get("mapping_rules"));
            boolean mappingChanged = !Objects.equals(previous, normalized);
            one.set("mapping_rules", normalized);
            // 只有映射内容真的发生变化才使已导出数据变为 stale；重复保存不破坏“已最新”。
            if (mappingChanged) {
                one.set("updated_time", LocalDateTime.now().toString());
                String username = currentUsername();
                if (StrUtil.isNotBlank(username)) {
                    one.set("updated_by", username);
                }
            }
            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("保存映射规则失败: " + e.getMessage());
        }
    }

    public AjaxResult openTaskPath(Map<String, Object> req) {
        String name = trim(req != null ? req.get("name") : null);
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(name)) return AjaxResult.error("任务不存在");
            Path path = resolveExportedTaskDirectory(name, true);
            if (path == null) {
                return AjaxResult.error("该任务还没有已导出的本地中间实例数据集，无法打开路径");
            }
            openDirectory(path);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path.toAbsolutePath().normalize().toString());
            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("打开路径失败: " + e.getMessage());
        }
    }

    public AjaxResult previewTask(String name, int requestedPerLabel) {
        return previewTask(name, requestedPerLabel, null);
    }

    public AjaxResult previewTask(String name, int requestedPerLabel, String baseUrl) {
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        int perLabel = Math.max(1, Math.min(requestedPerLabel, 12));
        Path taskRoot = WorkspacePathUtil.instanceDatasetMidRoot().resolve(name).normalize();
        Path imagesDir = taskRoot.resolve("images").normalize();
        Path cocoFile = taskRoot.resolve("annotations").resolve("instances.json");
        JSONObject debug = buildPreviewDebug(taskRoot, imagesDir, cocoFile);
        if (!Files.isRegularFile(cocoFile)) {
            JSONObject result = new JSONObject();
            result.set("task_name", name);
            result.set("debug", debug);
            return AjaxResult.error("该任务尚未导出，或缺少 COCO 标注文件：" + cocoFile.toAbsolutePath());
        }
        try {
            JSONObject coco = JSONUtil.parseObj(Files.readString(cocoFile, StandardCharsets.UTF_8));
            JSONArray categories = coco.getJSONArray("categories");
            JSONArray images = coco.getJSONArray("images");
            JSONArray annotations = coco.getJSONArray("annotations");
            Map<Integer, String> categoryNames = new LinkedHashMap<>();
            Map<Long, String> imageFiles = new HashMap<>();
            Map<Long, JSONObject> imageInfoById = new HashMap<>();
            if (categories != null) {
                for (Object value : categories) {
                    if (value instanceof JSONObject category && category.getInt("id") != null) {
                        categoryNames.put(category.getInt("id"), category.getStr("name", ""));
                    }
                }
            }
            if (images != null) {
                for (Object value : images) {
                    if (value instanceof JSONObject image && image.getLong("id") != null) {
                        imageFiles.put(image.getLong("id"), image.getStr("file_name", ""));
                        imageInfoById.put(image.getLong("id"), image);
                    }
                }
            }
            Map<Integer, LinkedHashSet<Long>> imageIdsByCategory = new LinkedHashMap<>();
            Map<Long, JSONArray> objectsByImage = new HashMap<>();
            categoryNames.keySet().forEach(id -> imageIdsByCategory.put(id, new LinkedHashSet<>()));
            if (annotations != null) {
                List<Object> shuffled = new ArrayList<>(annotations);
                Collections.shuffle(shuffled);
                for (Object value : shuffled) {
                    if (!(value instanceof JSONObject annotation)) continue;
                    Integer categoryId = annotation.getInt("category_id");
                    Long imageId = annotation.getLong("image_id");
                    if (categoryId != null && imageId != null && imageFiles.containsKey(imageId)) {
                        imageIdsByCategory.computeIfAbsent(categoryId, ignored -> new LinkedHashSet<>()).add(imageId);
                    }
                }
                for (Object value : annotations) {
                    if (!(value instanceof JSONObject annotation)) continue;
                    Long imageId = annotation.getLong("image_id");
                    Integer categoryId = annotation.getInt("category_id");
                    if (imageId == null || categoryId == null || !imageFiles.containsKey(imageId)) continue;
                    JSONObject object = cocoAnnotationToPreviewObject(annotation, categoryNames.get(categoryId));
                    if (object == null) continue;
                    objectsByImage.computeIfAbsent(imageId, ignored -> new JSONArray()).add(object);
                }
            }

            JSONArray items = new JSONArray();
            for (Map.Entry<Integer, String> category : categoryNames.entrySet()) {
                JSONArray selected = new JSONArray();
                for (Long imageId : imageIdsByCategory.getOrDefault(category.getKey(), new LinkedHashSet<>())) {
                    String fileName = imageFiles.get(imageId);
                    if (StrUtil.isBlank(fileName)) continue;
                    JSONObject image = new JSONObject();
                    image.set("file_name", fileName);
                    String relUrl = "/taskDatasetDev/tasks/preview/image?name="
                            + URLEncoder.encode(name, StandardCharsets.UTF_8)
                            + "&file=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                    image.set("url", StrUtil.isBlank(baseUrl) ? relUrl : baseUrl + relUrl);
                    JSONObject imageInfo = imageInfoById.get(imageId);
                    int width = imageInfo != null ? imageInfo.getInt("width", 0) : 0;
                    int height = imageInfo != null ? imageInfo.getInt("height", 0) : 0;
                    if (width <= 0 || height <= 0) {
                        int[] wh = readPreviewImageSize(name, fileName);
                        width = wh[0];
                        height = wh[1];
                    }
                    image.set("width", width);
                    image.set("height", height);
                    image.set("objects", objectsByImage.getOrDefault(imageId, new JSONArray()));
                    selected.add(image);
                    if (selected.size() >= perLabel) break;
                }
                if (selected.isEmpty() && images != null) {
                    for (Object value : images) {
                        if (!(value instanceof JSONObject imageInfo)) continue;
                        Long imageId = imageInfo.getLong("id");
                        String fileName = imageInfo.getStr("file_name", "");
                        if (imageId == null || StrUtil.isBlank(fileName)) continue;
                        Path imagePath = resolvePreviewImage(name, fileName);
                        if (imagePath == null) continue;
                        JSONObject image = new JSONObject();
                        image.set("file_name", fileName);
                        String relUrl = "/taskDatasetDev/tasks/preview/image?name="
                                + URLEncoder.encode(name, StandardCharsets.UTF_8)
                                + "&file=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                        image.set("url", StrUtil.isBlank(baseUrl) ? relUrl : baseUrl + relUrl);
                        int width = imageInfo.getInt("width", 0);
                        int height = imageInfo.getInt("height", 0);
                        if (width <= 0 || height <= 0) {
                            int[] wh = readPreviewImageSize(name, fileName);
                            width = wh[0];
                            height = wh[1];
                        }
                        image.set("width", width);
                        image.set("height", height);
                        image.set("objects", objectsByImage.getOrDefault(imageId, new JSONArray()));
                        image.set("fallback", true);
                        selected.add(image);
                        if (selected.size() >= perLabel) break;
                    }
                }
                JSONObject item = new JSONObject();
                item.set("label", category.getValue());
                item.set("images", selected);
                item.set("count", Math.max(imageIdsByCategory.getOrDefault(category.getKey(), new LinkedHashSet<>()).size(), selected.size()));
                items.add(item);
            }
            debug.set("coco_image_count", images == null ? 0 : images.size());
            debug.set("coco_annotation_count", annotations == null ? 0 : annotations.size());
            debug.set("coco_category_count", categories == null ? 0 : categories.size());
            JSONObject result = new JSONObject();
            result.set("task_name", name);
            result.set("per_label", perLabel);
            result.set("debug", debug);
            result.set("items", items);
            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("读取示例失败: " + e.getMessage());
        }
    }

    public AjaxResult previewObjects(String name, String fileName) {
        if (StrUtil.isBlank(name) || StrUtil.isBlank(fileName)) return AjaxResult.error("缺少任务名称或图片文件名");
        Path taskRoot = WorkspacePathUtil.instanceDatasetMidRoot().resolve(name).normalize();
        Path imagesDir = taskRoot.resolve("images").normalize();
        Path cocoFile = taskRoot.resolve("annotations").resolve("instances.json");
        JSONObject debug = buildPreviewDebug(taskRoot, imagesDir, cocoFile);
        Path image = resolvePreviewImage(name, fileName);
        if (image == null) {
            debug.set("requested_file", fileName);
            return AjaxResult.error("图片文件不存在或路径非法：" + fileName);
        }
        try {
            JSONObject coco = JSONUtil.parseObj(Files.readString(cocoFile, StandardCharsets.UTF_8));
            JSONArray categories = coco.getJSONArray("categories");
            JSONArray images = coco.getJSONArray("images");
            JSONArray annotations = coco.getJSONArray("annotations");
            Map<Integer, String> categoryNames = new LinkedHashMap<>();
            if (categories != null) {
                for (Object value : categories) {
                    if (value instanceof JSONObject category && category.getInt("id") != null) {
                        categoryNames.put(category.getInt("id"), category.getStr("name", ""));
                    }
                }
            }
            Set<Long> imageIds = new LinkedHashSet<>();
            String requested = fileName.replace("\\", "/");
            String requestedBase = baseName(requested);
            JSONObject matchedImage = null;
            if (images != null) {
                for (Object value : images) {
                    if (!(value instanceof JSONObject img)) continue;
                    Long id = img.getLong("id");
                    String one = img.getStr("file_name", "").replace("\\", "/");
                    if (id == null || StrUtil.isBlank(one)) continue;
                    String oneName = Paths.get(one).getFileName().toString();
                    if (one.equals(requested) || oneName.equals(Paths.get(requested).getFileName().toString()) || baseName(oneName).equals(requestedBase)) {
                        imageIds.add(id);
                        matchedImage = img;
                    }
                }
            }
            JSONArray objects = new JSONArray();
            if (annotations != null) {
                for (Object value : annotations) {
                    if (!(value instanceof JSONObject annotation)) continue;
                    Long imageId = annotation.getLong("image_id");
                    Integer categoryId = annotation.getInt("category_id");
                    if (imageId == null || !imageIds.contains(imageId)) continue;
                    JSONObject object = cocoAnnotationToPreviewObject(annotation, categoryNames.get(categoryId));
                    if (object != null) objects.add(object);
                }
            }
            int width = matchedImage != null ? matchedImage.getInt("width", 0) : 0;
            int height = matchedImage != null ? matchedImage.getInt("height", 0) : 0;
            if (width <= 0 || height <= 0) {
                int[] wh = readPreviewImageSize(name, fileName);
                width = wh[0];
                height = wh[1];
            }
            JSONObject result = new JSONObject();
            result.set("width", width);
            result.set("height", height);
            result.set("objects", objects);
            result.set("debug", debug);
            return AjaxResult.success(result);
        } catch (Exception e) {
            return AjaxResult.error("读取任务图片标注失败：" + e.getMessage());
        }
    }

    private String baseName(String fileName) {
        if (fileName == null) return "";
        String n = Paths.get(fileName.replace("\\", "/")).getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
    private JSONObject buildPreviewDebug(Path taskRoot, Path imagesDir, Path cocoFile) {
        JSONObject debug = new JSONObject();
        debug.set("task_root", taskRoot.toAbsolutePath().toString());
        debug.set("task_root_exists", Files.isDirectory(taskRoot));
        debug.set("images_dir", imagesDir.toAbsolutePath().toString());
        debug.set("images_dir_exists", Files.isDirectory(imagesDir));
        debug.set("annotation_file", cocoFile.toAbsolutePath().toString());
        debug.set("annotation_file_exists", Files.isRegularFile(cocoFile));
        return debug;
    }
    public Path resolvePreviewImage(String name, String fileName) {
        if (StrUtil.isBlank(name) || StrUtil.isBlank(fileName)) return null;
        Path images = WorkspacePathUtil.instanceDatasetMidRoot().resolve(name).resolve("images").normalize();
        String safeFile = fileName.replace("\\", "/");
        while (safeFile.startsWith("/")) safeFile = safeFile.substring(1);
        if (safeFile.contains("..")) return null;
        Path candidate = images.resolve(safeFile).normalize();
        if (!candidate.startsWith(images) || !Files.isRegularFile(candidate)) return null;
        return candidate;
    }

    private int[] readPreviewImageSize(String name, String fileName) {
        Path image = resolvePreviewImage(name, fileName);
        if (image == null) return new int[]{0, 0};
        try {
            BufferedImage bi = ImageIO.read(image.toFile());
            if (bi != null) return new int[]{bi.getWidth(), bi.getHeight()};
        } catch (Exception ignore) {}
        return new int[]{0, 0};
    }

    private JSONObject cocoAnnotationToPreviewObject(JSONObject annotation, String label) {
        if (annotation == null) return null;
        JSONArray points = null;
        JSONArray segmentation = annotation.getJSONArray("segmentation");
        if (segmentation != null && !segmentation.isEmpty()) {
            Object first = segmentation.get(0);
            JSONArray polygon = first instanceof JSONArray ? (JSONArray) first : segmentation;
            if (polygon != null && polygon.size() >= 6) {
                points = new JSONArray();
                for (int i = 0; i + 1 < polygon.size(); i += 2) {
                    JSONArray point = new JSONArray();
                    point.add(toDouble(polygon.get(i)));
                    point.add(toDouble(polygon.get(i + 1)));
                    points.add(point);
                }
            }
        }
        if (points == null || points.isEmpty()) {
            JSONArray bbox = annotation.getJSONArray("bbox");
            if (bbox == null || bbox.size() < 4) return null;
            double x = toDouble(bbox.get(0));
            double y = toDouble(bbox.get(1));
            double w = Math.max(0d, toDouble(bbox.get(2)));
            double h = Math.max(0d, toDouble(bbox.get(3)));
            points = new JSONArray();
            points.add(point(x, y));
            points.add(point(x + w, y));
            points.add(point(x + w, y + h));
            points.add(point(x, y + h));
        }
        JSONObject object = new JSONObject();
        object.set("label", StrUtil.blankToDefault(label, "unknown"));
        object.set("name", StrUtil.blankToDefault(label, "unknown"));
        object.set("points", points);
        return object;
    }

    private JSONArray point(double x, double y) {
        JSONArray p = new JSONArray();
        p.add(x);
        p.add(y);
        return p;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0d;
        }
    }

    private List<Map<String, Object>> readTasksAsList() throws Exception {
        JSONObject root = readTaskRoot();
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : root.keySet()) {
            JSONObject one = root.getJSONObject(key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", key);
            row.put("desc", one.getStr("desc", ""));
            row.put("target_schema", jsonArrayToList(one.getJSONArray("target_schema")));
            row.put("test_datasets", jsonArrayToList(one.getJSONArray("test_datasets")));
            row.put("mapping_rules", one.getJSONObject("mapping_rules"));
            row.put("updated_time", one.getStr("updated_time", ""));
            row.put("updated_by", one.getStr("updated_by", ""));
            row.put("last_export_time", one.getStr("last_export_time", ""));
            row.put("last_export_by", one.getStr("last_export_by", ""));
            row.put("last_export_mid_count", one.getInt("last_export_mid_count", 0));
            Path exportedDir = resolveExportedTaskDirectory(key, true);
            boolean hasExportedDataset = exportedDir != null;
            boolean currentExportFormat = hasExportedDataset
                    && Files.isDirectory(exportedDir.resolve("images"))
                    && Files.isRegularFile(exportedDir.resolve("annotations").resolve("instances.json"));
            row.put("has_exported_dataset", hasExportedDataset);
            row.put("export_format_current", currentExportFormat);
            row.put("export_path", hasExportedDataset ? exportedDir.toAbsolutePath().normalize().toString() : "");
            String statusCode = hasExportedDataset && !currentExportFormat
                    ? "stale"
                    : resolveExportStatusCode(one, hasExportedDataset);
            row.put("status_code", statusCode);
            row.put("status_text", statusText(statusCode));
            MappingStatus mappingStatus = assessMappingStatus(one);
            row.put("mapping_status_code", mappingStatus.code);
            row.put("mapping_status_text", mappingStatus.text);
            row.put("mapping_status_detail", mappingStatus.detail);
            out.add(row);
        }
        out.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        return out;
    }

    private JSONObject readTaskRoot() throws Exception {
        List<TaskDataset> rows = taskDatasetService.lambdaQuery()
                .orderByDesc(TaskDataset::getId)
                .list();
        LinkedHashMap<String, TaskDataset> latestByName = new LinkedHashMap<>();
        for (TaskDataset row : rows) {
            String name = trim(row.getName());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            latestByName.putIfAbsent(name, row);
        }

        JSONObject root = new JSONObject(new LinkedHashMap<>());
        List<String> names = new ArrayList<>(latestByName.keySet());
        names.sort(String::compareToIgnoreCase);
        for (String name : names) {
            TaskDataset row = latestByName.get(name);
            JSONObject one = new JSONObject();
            one.set("desc", StrUtil.blankToDefault(row.getTaskDesc(), ""));
            one.set("target_schema", parseJsonArrayOrDefault(row.getTargetSchema(), deriveTargetSchema(row)));
            one.set("test_datasets", parseJsonArrayOrDefault(row.getTestDatasets(), deriveTestDatasets(row)));
            one.set("mapping_rules", parseJsonObjectOrDefault(row.getMappingRules(), new JSONObject()));

            LocalDateTime updatedTime = row.getDevUpdatedTime() != null ? row.getDevUpdatedTime() : row.getCreatedTime();
            one.set("updated_time", updatedTime != null ? updatedTime.toString() : "");
            one.set("updated_by", StrUtil.blankToDefault(row.getDevUpdatedBy(), StrUtil.blankToDefault(row.getUsername(), "")));

            one.set("last_export_time", row.getLastExportTime() != null ? row.getLastExportTime().toString() : "");
            one.set("last_export_source_updated_time",
                    row.getLastExportSourceUpdatedTime() != null ? row.getLastExportSourceUpdatedTime().toString() : "");
            one.set("last_export_by", StrUtil.blankToDefault(row.getLastExportBy(), ""));
            one.set("last_export_mid_count", row.getLastExportMidCount() == null ? 0 : row.getLastExportMidCount());
            root.set(name, one);
        }
        return root;
    }

    private void writeTaskRoot(JSONObject root) throws Exception {
        List<TaskDataset> rows = taskDatasetService.lambdaQuery()
                .orderByDesc(TaskDataset::getId)
                .list();
        LinkedHashMap<String, TaskDataset> latestByName = new LinkedHashMap<>();
        for (TaskDataset row : rows) {
            String name = trim(row.getName());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            latestByName.putIfAbsent(name, row);
        }

        Set<String> targetNames = new LinkedHashSet<>(root.keySet());
        for (String existingName : latestByName.keySet()) {
            if (!targetNames.contains(existingName)) {
                taskDatasetService.remove(new LambdaQueryWrapper<TaskDataset>().eq(TaskDataset::getName, existingName));
            }
        }

        for (String name : root.keySet()) {
            JSONObject one = root.getJSONObject(name);
            if (one == null || StrUtil.isBlank(name)) {
                continue;
            }
            TaskDataset row = latestByName.get(name);
            boolean isNew = false;
            if (row == null) {
                row = new TaskDataset();
                row.setName(name);
                row.setSensorType("外部");
                row.setTargetType("复合");
                row.setDataFormat(0);
                row.setUsername(StrUtil.blankToDefault(currentUsername(), "admin"));
                row.setCreatedTime(LocalDateTime.now());
                row.setCoreId("");
                row.setCoreName("");
                row.setCoreTargetType("");
                row.setCoreImgNum(0);
                row.setCoreAnnoNum(0);
                row.setCoreClassNum(0);
                row.setCoreClassList("{}");
                row.setCoreDataPath("");
                row.setCoreAnnoPath("");
                row.setSupId("");
                row.setSupName("");
                row.setSupTargetType("");
                row.setSupImgNum(0);
                row.setSupAnnoNum(0);
                row.setSupClassNum(0);
                row.setSupClassList("{}");
                row.setSupDataPath("");
                row.setSupAnnoPath("");
                isNew = true;
            }

            row.setTaskDesc(one.getStr("desc", ""));
            row.setTargetSchema(JSONUtil.toJsonStr(parseJsonArrayOrDefault(one.get("target_schema"), new JSONArray())));
            row.setTestDatasets(JSONUtil.toJsonStr(parseJsonArrayOrDefault(one.get("test_datasets"), new JSONArray())));
            row.setMappingRules(JSONUtil.toJsonStr(parseJsonObjectOrDefault(one.get("mapping_rules"), new JSONObject())));
            row.setDevUpdatedTime(parseTime(one.getStr("updated_time", "")));
            row.setDevUpdatedBy(one.getStr("updated_by", ""));
            row.setLastExportTime(parseTime(one.getStr("last_export_time", "")));
            row.setLastExportSourceUpdatedTime(parseTime(one.getStr("last_export_source_updated_time", "")));
            row.setLastExportBy(one.getStr("last_export_by", ""));
            row.setLastExportMidCount(one.getInt("last_export_mid_count", 0));

            if (isNew) {
                taskDatasetService.save(row);
            } else {
                taskDatasetService.updateById(row);
            }
        }
    }

    private JSONArray deriveTargetSchema(TaskDataset row) {
        JSONArray arr = new JSONArray();
        String coreClassList = trim(row.getCoreClassList());
        if (StrUtil.isBlank(coreClassList)) {
            return arr;
        }
        try {
            if (coreClassList.startsWith("{")) {
                JSONObject obj = JSONUtil.parseObj(coreClassList);
                for (String k : obj.keySet()) {
                    if (StrUtil.isNotBlank(k)) arr.add(k);
                }
                return arr;
            }
            if (coreClassList.startsWith("[")) {
                JSONArray src = JSONUtil.parseArray(coreClassList);
                for (Object it : src) {
                    String one = trim(it);
                    if (StrUtil.isNotBlank(one)) arr.add(one);
                }
            }
        } catch (Exception ignored) {
        }
        return arr;
    }

    private JSONArray deriveTestDatasets(TaskDataset row) {
        JSONArray arr = new JSONArray();
        for (String one : splitUnderscore(row.getCoreName())) {
            if (StrUtil.isNotBlank(one)) {
                arr.add(one);
            }
        }
        return arr;
    }

    private JSONArray parseJsonArrayOrDefault(Object raw, JSONArray dft) {
        if (raw instanceof JSONArray ja) {
            return ja;
        }
        String txt = trim(raw);
        if (StrUtil.isBlank(txt)) {
            return dft;
        }
        try {
            return JSONUtil.parseArray(txt);
        } catch (Exception e) {
            return dft;
        }
    }

    private JSONObject parseJsonObjectOrDefault(Object raw, JSONObject dft) {
        if (raw instanceof JSONObject jo) {
            return jo;
        }
        if (raw instanceof Map<?, ?> map) {
            return JSONUtil.parseObj(map);
        }
        String txt = trim(raw);
        if (StrUtil.isBlank(txt)) {
            return dft;
        }
        try {
            return JSONUtil.parseObj(txt);
        } catch (Exception e) {
            return dft;
        }
    }

    private LocalDateTime parseTime(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> toStringList(Object obj) {
        List<String> out = new ArrayList<>();
        if (obj instanceof List<?> list) {
            for (Object it : list) {
                String s = trim(it);
                if (StrUtil.isNotBlank(s)) out.add(s);
            }
        }
        return out;
    }

    private List<String> jsonArrayToList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (Object it : arr) {
            String s = trim(it);
            if (StrUtil.isNotBlank(s)) out.add(s);
        }
        return out;
    }

    private JSONObject normalizeMappingRules(Object src) {
        JSONObject result = new JSONObject();
        if (!(src instanceof Map<?, ?> outer)) return result;
        for (Map.Entry<?, ?> entry : outer.entrySet()) {
            String datasetName = trim(entry.getKey());
            if (StrUtil.isBlank(datasetName) || !(entry.getValue() instanceof Map<?, ?> inner)) continue;
            JSONObject datasetMapping = new JSONObject();
            for (Map.Entry<?, ?> mapping : inner.entrySet()) {
                String from = trim(mapping.getKey());
                String to = trim(mapping.getValue());
                if (StrUtil.isBlank(from) || StrUtil.isBlank(to)) continue;
                datasetMapping.set(from, to);
            }
            result.set(datasetName, datasetMapping);
        }
        return result;
    }

    private String currentUsername() {
        try {
            User user = SessionUtil.getCurUser();
            return user != null ? user.getUsername() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void carryExportMeta(JSONObject src, JSONObject dst) {
        dst.set("last_export_time", src.getStr("last_export_time", ""));
        dst.set("last_export_source_updated_time", src.getStr("last_export_source_updated_time", ""));
        dst.set("last_export_by", src.getStr("last_export_by", ""));
        dst.set("last_export_mid_count", src.getInt("last_export_mid_count", 0));
    }

    private String resolveExportStatusCode(JSONObject task, boolean hasExportedDataset) {
        String updated = task.getStr("updated_time", "");
        String lastExportTime = task.getStr("last_export_time", "");
        String exportSnapshot = task.getStr("last_export_source_updated_time", "");
        if (StrUtil.isBlank(lastExportTime) || !hasExportedDataset) return "never_exported";
        if (!StrUtil.equals(updated, exportSnapshot)) return "stale";
        return "ready";
    }

    private String statusText(String code) {
        if (StrUtil.equals(code, "ready")) return "已最新";
        if (StrUtil.equals(code, "stale")) return "待更新";
        return "未导出";
    }

    private List<String> buildDefaultTestPlan(TaskDataset taskDataset) {
        List<String> testPlan = new ArrayList<>();
        List<Long> coreIds = new ArrayList<>();
        for (String id : splitIds(taskDataset.getCoreId())) {
            Long one = safeParseLong(id);
            if (one != null) {
                coreIds.add(one);
            }
        }
        if (coreIds.isEmpty()) return testPlan;

        List<OriginalDataset> originals = originalDatasetService.listByIds(coreIds);
        for (OriginalDataset original : originals) {
            List<String> testTaskIds = extractTestTaskIds(original.getTaskName(), original.getTaskId());
            for (String taskId : testTaskIds) {
                testPlan.add(original.getId() + "_" + taskId);
            }
        }
        return testPlan;
    }

    private List<String> extractTestTaskIds(String taskName, String taskId) {
        List<String> out = new ArrayList<>();
        String[] ids = splitUnderscore(taskId);
        String[] names = splitUnderscore(taskName);
        if (ids.length == 0 || names.length == 0) return out;

        boolean oneToOne = names.length == ids.length;
        if (oneToOne) {
            for (int i = 0; i < ids.length; i++) {
                if (names[i].toLowerCase(Locale.ROOT).contains("test")) {
                    out.add(ids[i]);
                }
            }
            return out;
        }

        for (int i = 0; i < ids.length; i++) {
            int idx = i * 2;
            if (idx + 1 >= names.length) break;
            String prefix = names[idx];
            if ("test".equalsIgnoreCase(prefix)) {
                out.add(ids[i]);
            }
        }
        return out;
    }

    private String[] splitUnderscore(String src) {
        if (StrUtil.isBlank(src)) return new String[0];
        return Arrays.stream(src.split("_"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toArray(String[]::new);
    }

    private List<String> splitIds(String src) {
        if (StrUtil.isBlank(src)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String one : splitUnderscore(src)) {
            if (StrUtil.isNotBlank(one)) out.add(one);
        }
        return out;
    }

    private TaskDataset findTaskDatasetForExport(String taskName, JSONObject taskDef) {
        TaskDataset exact = taskDatasetService.lambdaQuery()
                .eq(TaskDataset::getName, taskName)
                .orderByDesc(TaskDataset::getId)
                .last("limit 1")
                .one();
        if (exact != null) return exact;

        List<TaskDataset> candidates = taskDatasetService.lambdaQuery()
                .orderByDesc(TaskDataset::getId)
                .list();

        // 兜底1：名字模糊匹配（解决 dev 名称与 task_dataset 名称不完全一致，如 11111/1111）。
        TaskDataset fuzzyByName = findByNameSimilarity(taskName, candidates);
        if (fuzzyByName != null) {
            return fuzzyByName;
        }

        // 兜底2：按测试数据集覆盖匹配（优先与 dev 任务定义语义一致）。
        List<String> requiredNames = jsonArrayToList(taskDef.getJSONArray("test_datasets"));
        if (requiredNames.isEmpty()) return null;
        for (TaskDataset candidate : candidates) {
            Set<String> candidateNames = resolveTaskDatasetOriginalNames(candidate);
            if (!candidateNames.isEmpty() && candidateNames.containsAll(requiredNames)) {
                return candidate;
            }
        }
        return null;
    }

    private TaskDataset findByNameSimilarity(String taskName, List<TaskDataset> candidates) {
        String normDev = normalizeName(taskName);
        if (StrUtil.isBlank(normDev)) return null;
        for (TaskDataset candidate : candidates) {
            String name = candidate.getName();
            String normCandidate = normalizeName(name);
            if (StrUtil.isBlank(normCandidate)) continue;
            if (StrUtil.equals(normDev, normCandidate)) return candidate;
            if (normDev.contains(normCandidate) || normCandidate.contains(normDev)) return candidate;
            if (name != null && (name.equals(taskName) || name.contains(taskName) || taskName.contains(name))) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeName(String src) {
        if (StrUtil.isBlank(src)) return "";
        String lower = src.trim().toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^0-9a-z\\u4e00-\\u9fa5]+", "");
    }

    private Set<String> resolveTaskDatasetOriginalNames(TaskDataset taskDataset) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String id : splitIds(taskDataset.getCoreId())) {
            Long one = safeParseLong(id);
            if (one != null) ids.add(one);
        }
        for (String id : splitIds(taskDataset.getSupId())) {
            Long one = safeParseLong(id);
            if (one != null) ids.add(one);
        }
        if (ids.isEmpty()) return new LinkedHashSet<>();
        List<OriginalDataset> originals = originalDatasetService.listByIds(new ArrayList<>(ids));
        Set<String> names = new LinkedHashSet<>();
        for (OriginalDataset original : originals) {
            if (StrUtil.isNotBlank(original.getName())) {
                names.add(original.getName());
            }
        }
        return names;
    }

    private MappingStatus assessMappingStatus(JSONObject task) {
        List<String> targetSchema = jsonArrayToList(task.getJSONArray("target_schema"));
        Set<String> targetSet = new LinkedHashSet<>(targetSchema);
        List<String> testDatasets = jsonArrayToList(task.getJSONArray("test_datasets"));
        JSONObject mappingRules = task.getJSONObject("mapping_rules");

        if (targetSet.isEmpty()) {
            return new MappingStatus("error", "映射错误", "目标类别为空，请先配置目标类别。", false);
        }
        if (testDatasets.isEmpty()) {
            return new MappingStatus("error", "映射错误", "测试数据集为空，请先选择测试数据集。", false);
        }

        List<String> errors = new ArrayList<>();
        Set<String> incomingTargets = new LinkedHashSet<>();
        for (String datasetName : testDatasets) {
            JSONObject oneMap = mappingRules != null ? mappingRules.getJSONObject(datasetName) : null;
            if (oneMap == null) continue;

            List<String> illegalTarget = new ArrayList<>();
            for (String cls : oneMap.keySet()) {
                String mapped = oneMap.getStr(cls, "");
                if (StrUtil.isBlank(mapped)) continue;
                if (!targetSet.contains(mapped)) {
                    illegalTarget.add(cls + "->" + mapped);
                    continue;
                }
                incomingTargets.add(mapped);
            }
            if (!illegalTarget.isEmpty()) {
                errors.add("数据集 " + datasetName + " 映射目标非法: " + String.join(", ", illegalTarget));
            }
        }

        List<String> uncoveredTargets = new ArrayList<>();
        for (String target : targetSchema) {
            if (!incomingTargets.contains(target)) {
                uncoveredTargets.add(target);
            }
        }
        if (!uncoveredTargets.isEmpty()) {
            errors.add("以下目标类别暂无映射来源: " + String.join(", ", uncoveredTargets));
        }

        if (errors.isEmpty()) {
            return new MappingStatus("ok", "映射正确", "所有目标类别均有至少一个映射来源（允许多对一）。", true);
        }
        return new MappingStatus("error", "映射错误", String.join("；", errors), false);
    }

    private Long safeParseLong(String src) {
        try {
            return Long.parseLong(src);
        } catch (Exception e) {
            return null;
        }
    }

    private List<DatasetSource> resolveDatasetSources(List<String> datasetNames) {
        List<DatasetSource> out = new ArrayList<>();
        if (datasetNames == null || datasetNames.isEmpty()) return out;
        Map<String, ExternalRegistrySource> externalMap = readExternalRegistryMap();
        List<OriginalDataset> allOriginals = originalDatasetService.getAllOriginalDatasets();

        for (String name : datasetNames) {
            String n = trim(name);
            if (StrUtil.isBlank(n)) continue;
            ExternalRegistrySource ext = externalMap.get(n);
            if (ext != null && StrUtil.isNotBlank(ext.path)) {
                Path root = normalizeDatasetRoot(Paths.get(ext.path));
                if (root != null) {
                    out.add(new DatasetSource(n, root, ext.annotationDir));
                    continue;
                }
            }
            OriginalDataset latest = findLatestOriginalByName(allOriginals, n);
            if (latest != null) {
                Path root = normalizeDatasetRoot(parsePath(latest.getDataPath()));
                if (root != null) {
                    out.add(new DatasetSource(n, root));
                }
            }
        }
        return out;
    }

    private Map<String, ExternalRegistrySource> readExternalRegistryMap() {
        Map<String, ExternalRegistrySource> out = new LinkedHashMap<>();
        try {
            Path file = WorkspacePathUtil.resolveConfiguredPath(originalDatasetRoot, "data/original_dataset")
                    .resolve("external_dataset_registry.json");
            if (!Files.exists(file)) return out;
            String txt = Files.readString(file, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(txt)) return out;
            JSONObject obj = JSONUtil.parseObj(txt);
            JSONArray arr = obj.getJSONArray("datasets");
            if (arr == null) return out;
            for (Object item : arr) {
                if (!(item instanceof JSONObject jo)) continue;
                String name = trim(jo.get("name"));
                String path = trim(jo.get("path"));
                if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(path)) {
                    out.put(name, new ExternalRegistrySource(path, trim(jo.get("annotationDir"))));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private OriginalDataset findLatestOriginalByName(List<OriginalDataset> list, String name) {
        OriginalDataset latest = null;
        if (list == null) return null;
        for (OriginalDataset one : list) {
            if (!StrUtil.equals(trim(one.getName()), name)) continue;
            if (latest == null) {
                latest = one;
                continue;
            }
            long cur = one.getId() == null ? -1L : one.getId();
            long old = latest.getId() == null ? -1L : latest.getId();
            if (cur > old) latest = one;
        }
        return latest;
    }

    private Path parsePath(String raw) {
        String s = trim(raw);
        if (StrUtil.isBlank(s)) return null;
        return Paths.get(s).normalize();
    }

    private Path normalizeDatasetRoot(Path p) {
        if (p == null) return null;
        if (!Files.exists(p)) return null;
        Path n = p.normalize();
        String tail = n.getFileName() != null ? n.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if ("images".equals(tail) || "annotations".equals(tail)) {
            Path parent = n.getParent();
            return parent != null && Files.isDirectory(parent) ? parent : null;
        }
        return Files.isDirectory(n) ? n : null;
    }

    private int exportFromDatasetSources(String taskName, JSONObject taskDef, List<DatasetSource> sources) throws Exception {
        List<String> target = jsonArrayToList(taskDef.getJSONArray("target_schema"));
        JSONObject mappingRules = taskDef.getJSONObject("mapping_rules");
        LinkedHashMap<String, Long> mappedTargetCounts = new LinkedHashMap<>();
        for (String one : target) {
            if (StrUtil.isNotBlank(one)) mappedTargetCounts.put(one, 0L);
        }
        String exportName = taskName;
        Path outRoot = WorkspacePathUtil.instanceDatasetMidRoot().resolve(exportName).normalize();
        clearExportedMidByTask(taskName);
        Path outImages = outRoot.resolve("images");
        Path outAnnotations = outRoot.resolve("annotations");
        Files.createDirectories(outImages);
        Files.createDirectories(outAnnotations);

        CocoExportAccumulator acc = new CocoExportAccumulator(target, outImages, outAnnotations, mappedTargetCounts);
        int exportedSourceCount = 0;
        for (DatasetSource source : sources) {
            ResolvedExportPaths paths = resolveExportPaths(source.root, source.annotationDir);
            if (paths == null) continue;
            JSONObject oneMap = mappingRules != null ? mappingRules.getJSONObject(source.datasetName) : null;
            Map<String, String> labelMap = new LinkedHashMap<>();
            if (oneMap != null) {
                for (String k : oneMap.keySet()) {
                    String mapped = trim(oneMap.getStr(k, ""));
                    if (StrUtil.isBlank(mapped)) continue;
                    labelMap.put(k, mapped);
                }
            }
            if (labelMap.isEmpty()) {
                continue;
            }
            int before = acc.imageCount();
            exportSplitToCoco(source.datasetName, "train", paths.trainImages, paths.trainAnnos, labelMap, acc);
            exportSplitToCoco(source.datasetName, "test", paths.testImages, paths.testAnnos, labelMap, acc);
            if (acc.imageCount() > before) {
                exportedSourceCount++;
            }
        }
        if (acc.imageCount() > 0) {
            validatePairedMidExport(outImages, outAnnotations);
            Path cocoFile = outAnnotations.resolve("instances.json");
            Files.writeString(cocoFile, JSONUtil.toJsonPrettyStr(acc.toCoco()), StandardCharsets.UTF_8);

            InstanceDatasetMid mid = new InstanceDatasetMid();
            mid.setFatherName(taskName);
            mid.setName(exportName);
            mid.setSensorType("外部");
            mid.setTargetType("复合");
            mid.setDataFormat(0);
            mid.setClassList(JSONUtil.toJsonStr(mappedTargetCounts));
            mid.setClassNum(target.size());
            mid.setImgNum(acc.imageCount());
            mid.setAnnoNum(acc.annotationCount());
            // 中间数据集不再区分 train/test；为兼容现有四路径字段，二者都指向同一平铺目录。
            mid.setTrainImagePath(toPosix(outImages));
            mid.setTrainAnnoPath(toPosix(outAnnotations));
            mid.setTestImagePath(toPosix(outImages));
            mid.setTestAnnoPath(toPosix(outAnnotations));
            mid.setUsername(currentUsername());
            mid.setCreatedTime(LocalDateTime.now());
            mid.setUpdatedTime(LocalDateTime.now());
            saveMidRecord(mid);
        } else {
            deleteDirectoryRecursively(outRoot);
        }
        return exportedSourceCount;
    }

    private void validatePairedMidExport(Path imagesDir, Path annotationsDir) throws IOException {
        List<Path> images = new ArrayList<>();
        try (var walk = Files.walk(imagesDir)) {
            walk.filter(Files::isRegularFile).forEach(images::add);
        }
        if (images.isEmpty()) throw new IllegalStateException("标签映射后没有可导出的图片");
        List<String> missing = new ArrayList<>();
        for (Path image : images) {
            String fileName = image.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
            Path annotation = annotationsDir.resolve(stem + ".txt");
            if (!Files.isRegularFile(annotation) || Files.size(annotation) == 0L) missing.add(fileName);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("中间实例数据集导出不完整，图片缺少映射后的同名标注: "
                    + String.join(", ", missing.subList(0, Math.min(10, missing.size()))));
        }
    }

    private void exportSplitToCoco(String datasetName, String split, Path imageDir, Path annoDir,
                                   Map<String, String> labelMap, CocoExportAccumulator acc) throws IOException {
        if (imageDir == null || annoDir == null || !Files.isDirectory(imageDir) || !Files.isDirectory(annoDir)) return;
        List<Path> jsonFiles = new ArrayList<>();
        collectFilesBySuffix(annoDir, ".json", jsonFiles);
        boolean parsedCoco = false;
        for (Path json : jsonFiles) {
            parsedCoco |= exportOneCocoFile(datasetName, split, imageDir, json, labelMap, acc);
        }
        // COCO 与 TXT 不重复处理；没有有效 COCO 时再自动识别 DOTA/YOLO TXT。
        if (!parsedCoco) {
            exportTxtAnnotations(datasetName, split, imageDir, annoDir, labelMap, acc);
        }
    }

    private boolean exportOneCocoFile(String datasetName, String split, Path imageDir, Path jsonPath,
                                      Map<String, String> labelMap, CocoExportAccumulator acc) {
        try {
            JSONObject root = JSONUtil.parseObj(Files.readString(jsonPath, StandardCharsets.UTF_8));
            JSONArray images = root.getJSONArray("images");
            JSONArray annotations = root.getJSONArray("annotations");
            JSONArray categories = root.getJSONArray("categories");
            if (images == null || annotations == null || categories == null) return false;

            Map<Integer, String> oldCategoryToTarget = new HashMap<>();
            for (Object value : categories) {
                if (!(value instanceof JSONObject category)) continue;
                Integer id = category.getInt("id");
                String target = mappedTarget(labelMap, category.getStr("name", ""));
                if (id != null && StrUtil.isNotBlank(target)) oldCategoryToTarget.put(id, target);
            }
            if (oldCategoryToTarget.isEmpty()) return true;

            Map<Long, List<JSONObject>> annByImage = new LinkedHashMap<>();
            for (Object value : annotations) {
                if (!(value instanceof JSONObject annotation)) continue;
                Integer oldCategory = annotation.getInt("category_id");
                Long imageId = annotation.getLong("image_id");
                if (imageId == null || oldCategory == null || !oldCategoryToTarget.containsKey(oldCategory)) continue;
                JSONObject copy = JSONUtil.parseObj(annotation);
                copy.set("_target_name", oldCategoryToTarget.get(oldCategory));
                annByImage.computeIfAbsent(imageId, ignored -> new ArrayList<>()).add(copy);
            }
            if (annByImage.isEmpty()) return true;

            for (Object value : images) {
                if (!(value instanceof JSONObject image)) continue;
                Long oldImageId = image.getLong("id");
                List<JSONObject> mapped = oldImageId == null ? null : annByImage.get(oldImageId);
                if (mapped == null || mapped.isEmpty()) continue;
                String fileName = image.getStr("file_name", "");
                Path sourceImage = findImage(imageDir, fileName);
                if (sourceImage == null) continue;
                int width = image.getInt("width", 0);
                int height = image.getInt("height", 0);
                long newImageId = acc.addImage(datasetName, split, sourceImage, width, height);
                if (newImageId <= 0) continue;
                for (JSONObject annotation : mapped) {
                    String target = annotation.getStr("_target_name", "");
                    annotation.remove("_target_name");
                    acc.addCocoAnnotation(newImageId, target, annotation);
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void exportTxtAnnotations(String datasetName, String split, Path imageDir, Path annoDir,
                                      Map<String, String> labelMap, CocoExportAccumulator acc) throws IOException {
        List<Path> txtFiles = new ArrayList<>();
        collectFilesBySuffix(annoDir, ".txt", txtFiles);
        for (Path txt : txtFiles) {
            String name = txt.getFileName().toString();
            String stem = name.substring(0, name.length() - 4);
            Path image = findImage(imageDir, stem);
            if (image == null) continue;
            BufferedImage buffered = ImageIO.read(image.toFile());
            if (buffered == null) continue;
            int width = buffered.getWidth();
            int height = buffered.getHeight();
            List<MappedAnnotation> mapped = new ArrayList<>();
            for (String raw : Files.readAllLines(txt, StandardCharsets.UTF_8)) {
                MappedAnnotation one = parseTxtAnnotation(raw, width, height, labelMap);
                if (one != null) mapped.add(one);
            }
            if (mapped.isEmpty()) continue;
            long imageId = acc.addImage(datasetName, split, image, width, height);
            if (imageId <= 0) continue;
            for (MappedAnnotation one : mapped) acc.addMappedAnnotation(imageId, one);
        }
    }

    private MappedAnnotation parseTxtAnnotation(String raw, int imageWidth, int imageHeight,
                                                Map<String, String> labelMap) {
        String line = StrUtil.trim(raw);
        if (StrUtil.isBlank(line)) return null;
        String[] p = line.split("\\s+");
        try {
            if (p.length >= 9) {
                String target = mappedTarget(labelMap, p[8]);
                if (StrUtil.isBlank(target)) return null;
                List<Double> polygon = new ArrayList<>();
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                for (int i = 0; i < 8; i += 2) {
                    double x = Double.parseDouble(p[i]);
                    double y = Double.parseDouble(p[i + 1]);
                    polygon.add(x); polygon.add(y);
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
                return new MappedAnnotation(target, minX, minY, maxX - minX, maxY - minY, polygon);
            }
            if (p.length == 5) {
                String target = mappedTarget(labelMap, p[0]);
                if (StrUtil.isBlank(target)) target = mappedTarget(labelMap, "class_" + p[0]);
                if (StrUtil.isBlank(target)) return null;
                double a = Double.parseDouble(p[1]);
                double b = Double.parseDouble(p[2]);
                double c = Double.parseDouble(p[3]);
                double d = Double.parseDouble(p[4]);
                double x, y, w, h;
                if (a >= 0 && a <= 1 && b >= 0 && b <= 1 && c >= 0 && c <= 1 && d >= 0 && d <= 1) {
                    w = c * imageWidth; h = d * imageHeight;
                    x = a * imageWidth - w / 2.0; y = b * imageHeight - h / 2.0;
                } else {
                    x = Math.min(a, c); y = Math.min(b, d);
                    w = Math.abs(c - a); h = Math.abs(d - b);
                }
                return new MappedAnnotation(target, Math.max(0, x), Math.max(0, y), w, h, null);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private String mappedTarget(Map<String, String> labelMap, String sourceLabel) {
        if (StrUtil.isBlank(sourceLabel)) return null;
        String exact = labelMap.get(sourceLabel);
        if (StrUtil.isNotBlank(exact)) return exact;
        for (Map.Entry<String, String> entry : labelMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(sourceLabel)) return entry.getValue();
        }
        return null;
    }

    private Path findImage(Path imageDir, String fileNameOrStem) throws IOException {
        if (imageDir == null || StrUtil.isBlank(fileNameOrStem)) return null;
        Path direct = imageDir.resolve(fileNameOrStem).normalize();
        if (direct.startsWith(imageDir.normalize()) && Files.isRegularFile(direct)) return direct;
        String rawName = Paths.get(fileNameOrStem).getFileName().toString();
        int dot = rawName.lastIndexOf('.');
        String stem = dot > 0 ? rawName.substring(0, dot) : rawName;
        String[] extensions = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"};
        for (String ext : extensions) {
            Path candidate = imageDir.resolve(stem + ext);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        try (var files = Files.walk(imageDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String n = path.getFileName().toString();
                        int d = n.lastIndexOf('.');
                        return (d > 0 ? n.substring(0, d) : n).equalsIgnoreCase(stem);
                    })
                    .findFirst().orElse(null);
        }
    }

    private ExportStat exportSplitByMappedLabels(
            Path srcImgDir,
            Path srcAnnoDir,
            Path dstImgDir,
            Path dstAnnoDir,
            Map<String, String> labelMap,
            Map<String, Long> mappedTargetCounts) throws IOException {
        if (srcImgDir == null || srcAnnoDir == null || !Files.isDirectory(srcImgDir) || !Files.isDirectory(srcAnnoDir)) {
            return new ExportStat();
        }
        Files.createDirectories(dstImgDir);
        Files.createDirectories(dstAnnoDir);
        ExportStat coco = tryExportCocoByMappedLabels(srcImgDir, srcAnnoDir, dstImgDir, dstAnnoDir, labelMap, mappedTargetCounts);
        if (coco != null) {
            return coco;
        }
        // 非 COCO 结构暂按原有逻辑兜底（后续可继续扩展 DOTA/YOLO 的按标签过滤）
        ExportStat fallback = new ExportStat();
        fallback.imageCount = copyDirContent(srcImgDir, dstImgDir);
        fallback.annoCount = copyDirContent(srcAnnoDir, dstAnnoDir);
        return fallback;
    }

    private ExportStat tryExportCocoByMappedLabels(
            Path srcImgDir,
            Path srcAnnoDir,
            Path dstImgDir,
            Path dstAnnoDir,
            Map<String, String> labelMap,
            Map<String, Long> mappedTargetCounts) throws IOException {
        List<Path> jsonFiles = new ArrayList<>();
        collectFilesBySuffix(srcAnnoDir, ".json", jsonFiles);
        if (jsonFiles.isEmpty()) {
            return null;
        }

        ExportStat stat = new ExportStat();
        Set<String> copiedImageRelPaths = new HashSet<>();
        for (Path oneJson : jsonFiles) {
            JSONObject root;
            try {
                root = JSONUtil.parseObj(Files.readString(oneJson, StandardCharsets.UTF_8));
            } catch (Exception e) {
                continue;
            }
            JSONArray images = root.getJSONArray("images");
            JSONArray annotations = root.getJSONArray("annotations");
            JSONArray categories = root.getJSONArray("categories");
            if (images == null || annotations == null || categories == null) {
                continue;
            }

            LinkedHashMap<String, Integer> mappedCategoryIdByName = new LinkedHashMap<>();
            Map<Integer, Integer> oldCatIdToNewCatId = new HashMap<>();
            for (Object cObj : categories) {
                if (!(cObj instanceof JSONObject c)) continue;
                Integer id = c.getInt("id");
                String n = c.getStr("name", "");
                if (id == null || StrUtil.isBlank(n)) continue;
                String mapped = labelMap.get(n);
                if (StrUtil.isBlank(mapped)) continue;
                mappedCategoryIdByName.computeIfAbsent(mapped, k -> mappedCategoryIdByName.size() + 1);
                oldCatIdToNewCatId.put(id, mappedCategoryIdByName.get(mapped));
            }
            if (mappedCategoryIdByName.isEmpty()) {
                continue;
            }

            Set<Long> keepImageIds = new HashSet<>();
            List<JSONObject> candidateAnn = new ArrayList<>();
            for (Object aObj : annotations) {
                if (!(aObj instanceof JSONObject a)) continue;
                Integer catId = a.getInt("category_id");
                if (catId == null) continue;
                if (!oldCatIdToNewCatId.containsKey(catId)) continue;
                Long imageId = a.getLong("image_id");
                if (imageId != null) {
                    keepImageIds.add(imageId);
                }
                candidateAnn.add(a);
            }
            if (keepImageIds.isEmpty()) {
                continue;
            }

            JSONArray keptImages = new JSONArray();
            Set<Long> copiedImageIds = new HashSet<>();
            for (Object iObj : images) {
                if (!(iObj instanceof JSONObject i)) continue;
                Long id = i.getLong("id");
                if (id == null || !keepImageIds.contains(id)) continue;
                String fileName = i.getStr("file_name", "");
                if (StrUtil.isBlank(fileName)) continue;
                Path srcImage = srcImgDir.resolve(fileName).normalize();
                if (!Files.isRegularFile(srcImage)) {
                    continue;
                }
                Path dstImage = dstImgDir.resolve(fileName).normalize();
                Files.createDirectories(dstImage.getParent());
                Files.copy(srcImage, dstImage, StandardCopyOption.REPLACE_EXISTING);
                keptImages.add(i);
                copiedImageIds.add(id);
                String rel = fileName.replace("\\", "/");
                if (copiedImageRelPaths.add(rel)) {
                    stat.imageCount++;
                }
            }
            if (copiedImageIds.isEmpty()) {
                continue;
            }

            Map<Integer, String> newCatIdToName = new HashMap<>();
            for (Map.Entry<String, Integer> e : mappedCategoryIdByName.entrySet()) {
                newCatIdToName.put(e.getValue(), e.getKey());
            }
            JSONArray keptAnn = new JSONArray();
            for (JSONObject a : candidateAnn) {
                Long imageId = a.getLong("image_id");
                if (imageId == null || !copiedImageIds.contains(imageId)) continue;
                JSONObject one = JSONUtil.parseObj(a);
                Integer oldCatId = one.getInt("category_id");
                Integer newCatId = oldCatId == null ? null : oldCatIdToNewCatId.get(oldCatId);
                if (newCatId == null) continue;
                one.set("category_id", newCatId);
                keptAnn.add(one);
                stat.annoCount++;
                String mappedTarget = newCatIdToName.getOrDefault(newCatId, "");
                if (StrUtil.isNotBlank(mappedTarget) && mappedTargetCounts.containsKey(mappedTarget)) {
                    mappedTargetCounts.put(mappedTarget, mappedTargetCounts.get(mappedTarget) + 1);
                }
            }

            JSONArray keptCategories = new JSONArray();
            for (Map.Entry<String, Integer> e : mappedCategoryIdByName.entrySet()) {
                JSONObject c = new JSONObject();
                c.set("id", e.getValue());
                c.set("name", e.getKey());
                keptCategories.add(c);
            }

            JSONObject out = new JSONObject(new LinkedHashMap<>());
            for (String k : root.keySet()) {
                if ("images".equals(k) || "annotations".equals(k) || "categories".equals(k)) continue;
                out.set(k, root.get(k));
            }
            out.set("images", keptImages);
            out.set("annotations", keptAnn);
            out.set("categories", keptCategories);

            Path relJson = srcAnnoDir.relativize(oneJson);
            Path dstJson = dstAnnoDir.resolve(relJson);
            Files.createDirectories(dstJson.getParent());
            Files.writeString(dstJson, JSONUtil.toJsonPrettyStr(out), StandardCharsets.UTF_8);
        }
        return stat;
    }

    private void collectFilesBySuffix(Path dir, String suffix, List<Path> out) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    collectFilesBySuffix(child, suffix, out);
                    continue;
                }
                String n = child.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(suffix)) out.add(child);
            }
        }
    }

    private ResolvedExportPaths resolveExportPaths(Path datasetRoot) {
        return resolveExportPaths(datasetRoot, null);
    }

    private ResolvedExportPaths resolveExportPaths(Path datasetRoot, String selectedAnnotationDir) {
        if (datasetRoot == null || !Files.isDirectory(datasetRoot)) return null;
        Path images = datasetRoot.resolve("images");
        String annName = StrUtil.blankToDefault(selectedAnnotationDir, "annotations");
        if (annName.contains("/") || annName.contains("\\") || annName.contains("..")) return null;
        Path annos = datasetRoot.resolve(annName).normalize();
        if (annos.getParent() == null || !annos.getParent().equals(datasetRoot)) return null;
        Path trainImages = datasetRoot.resolve("train").resolve("images");
        Path trainAnnos = datasetRoot.resolve("train").resolve("anno");
        Path testImages = datasetRoot.resolve("test").resolve("images");
        Path testAnnos = datasetRoot.resolve("test").resolve("anno");

        if (Files.isDirectory(trainImages) && Files.isDirectory(trainAnnos)) {
            return new ResolvedExportPaths(
                    trainImages, trainAnnos,
                    Files.isDirectory(testImages) ? testImages : null,
                    Files.isDirectory(testAnnos) ? testAnnos : null
            );
        }
        if (Files.isDirectory(images) && Files.isDirectory(annos)) {
            Path trainSplitImg = images.resolve("train");
            Path trainSplitAnno = annos.resolve("train");
            Path testSplitImg = images.resolve("test");
            Path testSplitAnno = annos.resolve("test");
            if (Files.isDirectory(trainSplitImg) && Files.isDirectory(trainSplitAnno)) {
                return new ResolvedExportPaths(
                        trainSplitImg, trainSplitAnno,
                        Files.isDirectory(testSplitImg) ? testSplitImg : null,
                        Files.isDirectory(testSplitAnno) ? testSplitAnno : null
                );
            }
            return new ResolvedExportPaths(images, annos, null, null);
        }
        return null;
    }

    private int copyDirContent(Path srcDir, Path dstDir) throws IOException {
        if (srcDir == null || !Files.isDirectory(srcDir)) return 0;
        Files.createDirectories(dstDir);
        int copied = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(srcDir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    copied += copyDirContent(child, dstDir.resolve(child.getFileName().toString()));
                    continue;
                }
                Files.copy(child, dstDir.resolve(child.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        }
        return copied;
    }

    /** 是否存在可清除的中间导出（中间表记录或 instance_dataset_mid 下对应目录） */
    private boolean hasExportedMidArtifacts(String taskName) throws IOException {
        if (StrUtil.isBlank(taskName)) return false;
        if (countMidByFatherName(taskName) > 0) return true;
        Path root = WorkspacePathUtil.instanceDatasetMidRoot();
        if (!Files.isDirectory(root)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                String folder = child.getFileName().toString();
                if (folder.equals(taskName) || folder.startsWith(taskName + "_")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 定位任务对应的中间实例数据集目录。requireData=true 时至少包含一个实际文件，
     * 防止导出失败后遗留的空目录被误判为“已最新”。
     */
    private Path resolveExportedTaskDirectory(String taskName, boolean requireData) throws IOException {
        if (StrUtil.isBlank(taskName)) return null;
        Path root = WorkspacePathUtil.instanceDatasetMidRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return null;

        Path exact = root.resolve(taskName).normalize();
        if (exact.startsWith(root) && Files.isDirectory(exact)
                && (!requireData || directoryContainsFile(exact))) {
            return exact;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                String folder = child.getFileName().toString();
                Path normalized = child.toAbsolutePath().normalize();
                if (normalized.startsWith(root)
                        && folder.startsWith(taskName + "_")
                        && (!requireData || directoryContainsFile(normalized))) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private boolean directoryContainsFile(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return false;
        try (var files = Files.walk(dir)) {
            return files.anyMatch(Files::isRegularFile);
        }
    }

    private void openDirectory(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IOException("目录不存在: " + normalized);
        }
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(normalized.toFile());
                return;
            } catch (IOException | UnsupportedOperationException ignored) {
                // 某些 Windows/JDK 组合 Desktop.open 会返回不明确错误，继续使用 explorer.exe。
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder builder;
        if (os.contains("win")) {
            builder = new ProcessBuilder("explorer.exe", "/e,", normalized.toString());
        } else if (os.contains("mac")) {
            builder = new ProcessBuilder("open", normalized.toString());
        } else {
            builder = new ProcessBuilder("xdg-open", normalized.toString());
        }
        builder.start();
    }

    private void clearExportedMidByTask(String taskName) throws IOException {
        if (StrUtil.isBlank(taskName)) return;
        String table = resolveMidTableName();
        if (StrUtil.isNotBlank(table)) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE father_name = ?", taskName);
        }
        Path root = WorkspacePathUtil.instanceDatasetMidRoot();
        if (!Files.isDirectory(root)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                String folder = child.getFileName().toString();
                if (folder.equals(taskName) || folder.startsWith(taskName + "_")) {
                    deleteDirectoryRecursively(child);
                }
            }
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String toPosix(Path p) {
        String s = p.normalize().toString().replace("\\", "/");
        return s.endsWith("/") ? s : s + "/";
    }

    private void saveMidRecord(InstanceDatasetMid mid) {
        String table = resolveMidTableName();
        if (StrUtil.equals(table, "instance_dataset_mid")) {
            instanceDatasetMidService.save(mid);
            return;
        }
        if (StrUtil.equals(table, "instance_dataset")) {
            String sql = "INSERT INTO instance_dataset " +
                    "(father_name, name, sensor_type, target_type, img_num, anno_num, class_num, class_list, " +
                    "train_image_path, train_anno_path, test_image_path, test_anno_path, data_format, username, created_time, updated_time) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            jdbcTemplate.update(sql,
                    mid.getFatherName(),
                    mid.getName(),
                    mid.getSensorType(),
                    mid.getTargetType(),
                    mid.getImgNum(),
                    mid.getAnnoNum(),
                    mid.getClassNum(),
                    mid.getClassList(),
                    mid.getTrainImagePath(),
                    mid.getTrainAnnoPath(),
                    mid.getTestImagePath(),
                    mid.getTestAnnoPath(),
                    mid.getDataFormat(),
                    mid.getUsername(),
                    mid.getCreatedTime(),
                    mid.getUpdatedTime());
            return;
        }
        throw new IllegalStateException("未找到可用中间实例数据集表（instance_dataset_mid 或 instance_dataset）");
    }

    private String resolveMidTableName() {
        if (tableExists("instance_dataset_mid")) return "instance_dataset_mid";
        if (tableExists("instance_dataset")) return "instance_dataset";
        return "";
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static class MappedAnnotation {
        final String target;
        final double x;
        final double y;
        final double width;
        final double height;
        final List<Double> polygon;

        MappedAnnotation(String target, double x, double y, double width, double height, List<Double> polygon) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.polygon = polygon;
        }
    }

    private static class CocoExportAccumulator {
        private final JSONArray images = new JSONArray();
        private final JSONArray annotations = new JSONArray();
        private final JSONArray categories = new JSONArray();
        private final Map<String, Integer> categoryIds = new LinkedHashMap<>();
        private final Map<Path, Long> sourceImageIds = new HashMap<>();
        private final Map<Long, String> outputImageNames = new HashMap<>();
        private final Path outputImages;
        private final Path outputAnnotations;
        private final Map<String, Long> targetCounts;
        private long nextImageId = 1;
        private long nextAnnotationId = 1;

        CocoExportAccumulator(List<String> targets, Path outputImages, Path outputAnnotations,
                              Map<String, Long> targetCounts) {
            this.outputImages = outputImages;
            this.outputAnnotations = outputAnnotations;
            this.targetCounts = targetCounts;
            for (String target : targets) {
                if (StrUtil.isBlank(target) || categoryIds.containsKey(target)) continue;
                int id = categoryIds.size() + 1;
                categoryIds.put(target, id);
                JSONObject category = new JSONObject();
                category.set("id", id);
                category.set("name", target);
                category.set("supercategory", "object");
                categories.add(category);
            }
        }

        int imageCount() {
            return images.size();
        }

        int annotationCount() {
            return annotations.size();
        }

        long addImage(String datasetName, String split, Path source, int width, int height) throws IOException {
            Path key = source.toAbsolutePath().normalize();
            Long existing = sourceImageIds.get(key);
            if (existing != null) return existing;
            if (width <= 0 || height <= 0) {
                BufferedImage image = ImageIO.read(source.toFile());
                if (image == null) return -1;
                width = image.getWidth();
                height = image.getHeight();
            }
            String original = source.getFileName().toString();
            String prefix = safeName(datasetName) + "_" + safeName(split) + "_" + nextImageId + "_";
            String outputName = prefix + safeName(original);
            Path output = outputImages.resolve(outputName).normalize();
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);

            long id = nextImageId++;
            JSONObject image = new JSONObject();
            image.set("id", id);
            image.set("file_name", outputName);
            image.set("width", width);
            image.set("height", height);
            images.add(image);
            sourceImageIds.put(key, id);
            outputImageNames.put(id, outputName);
            return id;
        }

        void addCocoAnnotation(long imageId, String target, JSONObject source) {
            Integer categoryId = categoryIds.get(target);
            if (categoryId == null) return;
            JSONObject out = JSONUtil.parseObj(source);
            out.set("id", nextAnnotationId++);
            out.set("image_id", imageId);
            out.set("category_id", categoryId);
            out.set("iscrowd", out.getInt("iscrowd", 0));
            if (out.get("area") == null && out.getJSONArray("bbox") != null && out.getJSONArray("bbox").size() >= 4) {
                JSONArray bbox = out.getJSONArray("bbox");
                Double width = bbox.getDouble(2);
                Double height = bbox.getDouble(3);
                out.set("area", Math.max(0D, width == null ? 0D : width)
                        * Math.max(0D, height == null ? 0D : height));
            }
            annotations.add(out);
            appendDotaAnnotation(imageId, target, polygonFromCoco(out));
            targetCounts.computeIfPresent(target, (ignored, count) -> count + 1);
        }

        void addMappedAnnotation(long imageId, MappedAnnotation source) {
            Integer categoryId = categoryIds.get(source.target);
            if (categoryId == null || source.width <= 0 || source.height <= 0) return;
            JSONObject out = new JSONObject();
            out.set("id", nextAnnotationId++);
            out.set("image_id", imageId);
            out.set("category_id", categoryId);
            out.set("bbox", List.of(source.x, source.y, source.width, source.height));
            out.set("area", source.width * source.height);
            out.set("iscrowd", 0);
            if (source.polygon != null && source.polygon.size() >= 6) {
                out.set("segmentation", List.of(source.polygon));
            } else {
                out.set("segmentation", new JSONArray());
            }
            annotations.add(out);
            appendDotaAnnotation(imageId, source.target, polygonFromMapped(source));
            targetCounts.computeIfPresent(source.target, (ignored, count) -> count + 1);
        }

        private List<Double> polygonFromCoco(JSONObject annotation) {
            JSONArray segmentation = annotation.getJSONArray("segmentation");
            if (segmentation != null && !segmentation.isEmpty()) {
                Object first = segmentation.get(0);
                if (first instanceof JSONArray arr && arr.size() >= 8) {
                    List<Double> points = new ArrayList<>();
                    for (int i = 0; i < 8; i++) points.add(arr.getDouble(i));
                    return points;
                }
            }
            JSONArray bbox = annotation.getJSONArray("bbox");
            if (bbox == null || bbox.size() < 4) return Collections.emptyList();
            double x = bbox.getDouble(0, 0D), y = bbox.getDouble(1, 0D);
            double w = bbox.getDouble(2, 0D), h = bbox.getDouble(3, 0D);
            return List.of(x, y, x + w, y, x + w, y + h, x, y + h);
        }

        private List<Double> polygonFromMapped(MappedAnnotation source) {
            if (source.polygon != null && source.polygon.size() >= 8) return source.polygon.subList(0, 8);
            return List.of(source.x, source.y, source.x + source.width, source.y,
                    source.x + source.width, source.y + source.height, source.x, source.y + source.height);
        }

        private void appendDotaAnnotation(long imageId, String target, List<Double> polygon) {
            String imageName = outputImageNames.get(imageId);
            if (StrUtil.isBlank(imageName) || polygon == null || polygon.size() < 8) return;
            int dot = imageName.lastIndexOf('.');
            String stem = dot > 0 ? imageName.substring(0, dot) : imageName;
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) line.append(' ');
                line.append(polygon.get(i));
            }
            line.append(' ').append(target).append(" 0\n");
            try {
                Files.writeString(outputAnnotations.resolve(stem + ".txt"), line.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("写入映射标注失败: " + imageName, e);
            }
        }

        JSONObject toCoco() {
            JSONObject root = new JSONObject(new LinkedHashMap<>());
            root.set("info", Map.of("description", "AI_TT_PLATFORM mapped task dataset export"));
            root.set("licenses", new JSONArray());
            root.set("images", images);
            root.set("annotations", annotations);
            root.set("categories", categories);
            return root;
        }

        private static String safeName(String value) {
            String safe = StrUtil.blankToDefault(value, "item").replaceAll("[^0-9A-Za-z._\\-\\u4e00-\\u9fff]+", "_");
            return StrUtil.blankToDefault(safe, "item");
        }
    }

    private static class DatasetSource {
        final String datasetName;
        final Path root;
        final String annotationDir;

        DatasetSource(String datasetName, Path root) {
            this(datasetName, root, null);
        }

        DatasetSource(String datasetName, Path root, String annotationDir) {
            this.datasetName = datasetName;
            this.root = root;
            this.annotationDir = annotationDir;
        }
    }

    private static class ExternalRegistrySource {
        final String path;
        final String annotationDir;

        ExternalRegistrySource(String path, String annotationDir) {
            this.path = path;
            this.annotationDir = annotationDir;
        }
    }

    private static class ExportStat {
        int imageCount;
        int annoCount;
    }

    private static class ResolvedExportPaths {
        final Path trainImages;
        final Path trainAnnos;
        final Path testImages;
        final Path testAnnos;

        ResolvedExportPaths(Path trainImages, Path trainAnnos, Path testImages, Path testAnnos) {
            this.trainImages = trainImages;
            this.trainAnnos = trainAnnos;
            this.testImages = testImages;
            this.testAnnos = testAnnos;
        }
    }

    private static class MappingStatus {
        final String code;
        final String text;
        final String detail;
        final boolean ok;

        MappingStatus(String code, String text, String detail, boolean ok) {
            this.code = code;
            this.text = text;
            this.detail = detail;
            this.ok = ok;
        }
    }

    private int countMidByFatherName(String name) {
        String table = resolveMidTableName();
        if (StrUtil.isBlank(table)) return 0;
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM " + table + " WHERE father_name = ?",
                    Integer.class,
                    name);
            return cnt == null ? 0 : cnt;
        } catch (Exception e) {
            return 0;
        }
    }

    private String trim(Object obj) {
        return obj == null ? "" : StrUtil.trimToEmpty(String.valueOf(obj));
    }
}
