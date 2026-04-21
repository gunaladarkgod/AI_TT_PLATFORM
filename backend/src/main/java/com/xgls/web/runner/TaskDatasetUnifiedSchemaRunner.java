package com.xgls.web.runner;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Slf4j
@Component
@Order(3)
public class TaskDatasetUnifiedSchemaRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${sys.task-dataset-dev-file:/home/omen1/AI_TT_Platform/data/task_dataset_dev/tasks.json}")
    private String taskDatasetDevFile;

    public TaskDatasetUnifiedSchemaRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureUnifiedColumns();
            mergeLegacyDevJsonToTaskDataset();
        } catch (Exception e) {
            log.warn("[task-dataset] 统一表结构/数据合并失败: {}", e.toString());
        }
    }

    private void ensureUnifiedColumns() {
        addColumnIfMissing("task_dataset", "task_desc", "varchar(1024) NULL COMMENT 'Dev任务描述'");
        addColumnIfMissing("task_dataset", "target_schema", "longtext NULL COMMENT '目标类别定义(JSON数组字符串)'");
        addColumnIfMissing("task_dataset", "test_datasets", "longtext NULL COMMENT '测试数据集名称列表(JSON数组字符串)'");
        addColumnIfMissing("task_dataset", "mapping_rules", "longtext NULL COMMENT '类别映射规则(JSON对象字符串)'");
        addColumnIfMissing("task_dataset", "dev_updated_time", "datetime NULL COMMENT 'Dev任务定义更新时间'");
        addColumnIfMissing("task_dataset", "dev_updated_by", "varchar(64) NULL COMMENT 'Dev任务定义更新人'");
        addColumnIfMissing("task_dataset", "last_export_time", "datetime NULL COMMENT '最近导出中间实例数据集时间'");
        addColumnIfMissing("task_dataset", "last_export_source_updated_time", "datetime NULL COMMENT '最近导出时对应源定义更新时间'");
        addColumnIfMissing("task_dataset", "last_export_by", "varchar(64) NULL COMMENT '最近导出人'");
        addColumnIfMissing("task_dataset", "last_export_mid_count", "int unsigned NOT NULL DEFAULT 0 COMMENT '最近导出中间集数量'");
    }

    private void mergeLegacyDevJsonToTaskDataset() {
        Path file = Paths.get(taskDatasetDevFile).normalize();
        if (!Files.exists(file)) {
            return;
        }
        try {
            String txt = Files.readString(file, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(txt)) {
                return;
            }
            Object obj = JSONUtil.parse(txt);
            if (!(obj instanceof JSONObject root)) {
                return;
            }
            int merged = 0;
            for (String taskName : root.keySet()) {
                JSONObject one = root.getJSONObject(taskName);
                if (one == null || StrUtil.isBlank(taskName)) {
                    continue;
                }
                String desc = one.getStr("desc", "");
                JSONArray targetSchema = one.getJSONArray("target_schema");
                JSONArray testDatasets = one.getJSONArray("test_datasets");
                JSONObject mappingRules = one.getJSONObject("mapping_rules");
                LocalDateTime updatedTime = parseTime(one.getStr("updated_time", ""));
                String updatedBy = one.getStr("updated_by", "");
                LocalDateTime lastExportTime = parseTime(one.getStr("last_export_time", ""));
                LocalDateTime lastExportSourceUpdatedTime = parseTime(one.getStr("last_export_source_updated_time", ""));
                String lastExportBy = one.getStr("last_export_by", "");
                Integer lastExportMidCount = one.getInt("last_export_mid_count", 0);

                Long id = latestIdByName(taskName);
                if (id == null) {
                    jdbcTemplate.update(
                            "INSERT INTO task_dataset " +
                                    "(name, sensor_type, target_type, data_format, username, created_time, " +
                                    "core_id, core_name, core_target_type, core_img_num, core_anno_num, core_class_num, core_class_list, core_data_path, core_anno_path, " +
                                    "sup_id, sup_name, sup_target_type, sup_img_num, sup_anno_num, sup_class_num, sup_class_list, sup_data_path, sup_anno_path, " +
                                    "task_desc, target_schema, test_datasets, mapping_rules, dev_updated_time, dev_updated_by, last_export_time, last_export_source_updated_time, last_export_by, last_export_mid_count) " +
                                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            taskName, "外部", "复合", 0, StrUtil.blankToDefault(updatedBy, "admin"), Timestamp.valueOf(LocalDateTime.now()),
                            "", "", "", 0, 0, 0, "{}", "", "",
                            "", "", "", 0, 0, 0, "{}", "", "",
                            desc,
                            targetSchema == null ? "[]" : JSONUtil.toJsonStr(targetSchema),
                            testDatasets == null ? "[]" : JSONUtil.toJsonStr(testDatasets),
                            mappingRules == null ? "{}" : JSONUtil.toJsonStr(mappingRules),
                            toTimestamp(updatedTime),
                            updatedBy,
                            toTimestamp(lastExportTime),
                            toTimestamp(lastExportSourceUpdatedTime),
                            lastExportBy,
                            lastExportMidCount == null ? 0 : lastExportMidCount
                    );
                    merged++;
                    continue;
                }
                jdbcTemplate.update(
                        "UPDATE task_dataset SET task_desc=?, target_schema=?, test_datasets=?, mapping_rules=?, dev_updated_time=?, dev_updated_by=?, " +
                                "last_export_time=?, last_export_source_updated_time=?, last_export_by=?, last_export_mid_count=? WHERE id=?",
                        desc,
                        targetSchema == null ? "[]" : JSONUtil.toJsonStr(targetSchema),
                        testDatasets == null ? "[]" : JSONUtil.toJsonStr(testDatasets),
                        mappingRules == null ? "{}" : JSONUtil.toJsonStr(mappingRules),
                        toTimestamp(updatedTime),
                        updatedBy,
                        toTimestamp(lastExportTime),
                        toTimestamp(lastExportSourceUpdatedTime),
                        lastExportBy,
                        lastExportMidCount == null ? 0 : lastExportMidCount,
                        id
                );
                merged++;
            }
            if (merged > 0) {
                log.info("[task-dataset] 已将 legacy dev 定义合并入 task_dataset: {} 条", merged);
            }
        } catch (Exception e) {
            log.warn("[task-dataset] 合并 legacy dev json 失败: {}", e.toString());
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Integer.class,
                    tableName, columnName
            );
            if (cnt != null && cnt > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddl);
            log.info("[task-dataset] 已补齐列: {}.{}", tableName, columnName);
        } catch (Exception e) {
            log.warn("[task-dataset] 补齐列失败 {}.{}: {}", tableName, columnName, e.toString());
        }
    }

    private Long latestIdByName(String name) {
        try {
            return jdbcTemplate.query(
                    "SELECT id FROM task_dataset WHERE name = ? ORDER BY id DESC LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null,
                    name
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseTime(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Timestamp toTimestamp(LocalDateTime t) {
        return t == null ? null : Timestamp.valueOf(t);
    }
}
