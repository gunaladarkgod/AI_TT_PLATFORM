package com.xgls.web.runner;

import com.xgls.web.config.DatabaseChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 在 Flyway 未执行或 V59 未应用时，补齐 {@code instance_dataset_mid} 与 {@code instance_dataset} 缺失列，
 * 并把仍留在 {@code instance_dataset} 中的中间导出记录迁到 mid 表，避免预处理源列表为空、MyBatis 查最终集实体报错。
 */
@Component
@Slf4j
@Order(2)
public class InstanceDatasetSchemaCompatibilityRunner implements ApplicationRunner {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseChecker databaseChecker;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        while (!databaseChecker.isMySqlConnected()) {
            log.warn("[schema-compat] waiting for MySQL...");
            Thread.sleep(3000);
        }
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        try {
            ensureInstanceDatasetMidTable(jt);
            ensureInstanceDatasetOptionalColumns(jt);
            backfillMidFromLegacyInstanceDataset(jt);
        } catch (Exception e) {
            log.error("[schema-compat] 实例数据集表结构兼容处理失败（可检查库权限/SQL 模式）: {}", e.toString(), e);
        }
    }

    private void ensureInstanceDatasetMidTable(JdbcTemplate jt) {
        if (tableExists(jt, "instance_dataset_mid")) {
            return;
        }
        log.info("[schema-compat] 创建缺失表 instance_dataset_mid");
        jt.execute(
                """
                CREATE TABLE `instance_dataset_mid` (
                    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                    `father_name` varchar(255) NOT NULL COMMENT '任务数据集名称',
                    `name` varchar(255) NOT NULL COMMENT '数据集名称',
                    `sensor_type` varchar(15) DEFAULT NULL,
                    `target_type` varchar(15) DEFAULT NULL,
                    `img_num` int unsigned NOT NULL DEFAULT '0',
                    `anno_num` int unsigned NOT NULL DEFAULT '0',
                    `class_num` int unsigned DEFAULT '0',
                    `class_list` json DEFAULT NULL,
                    `train_image_path` varchar(1024) DEFAULT NULL,
                    `train_anno_path` varchar(1024) DEFAULT NULL,
                    `test_image_path` varchar(1024) DEFAULT NULL,
                    `test_anno_path` varchar(1024) DEFAULT NULL,
                    `data_format` tinyint unsigned NOT NULL DEFAULT '0',
                    `username` varchar(64) DEFAULT NULL,
                    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void ensureInstanceDatasetOptionalColumns(JdbcTemplate jt) {
        if (!tableExists(jt, "instance_dataset")) {
            return;
        }
        addColumnIfMissing(
                jt,
                "instance_dataset",
                "config_list",
                "ADD COLUMN `config_list` json NULL COMMENT '预处理链路'");
        addColumnIfMissing(
                jt,
                "instance_dataset",
                "param_schema",
                "ADD COLUMN `param_schema` json NULL COMMENT '实际参数'");
    }

    private void backfillMidFromLegacyInstanceDataset(JdbcTemplate jt) {
        if (!tableExists(jt, "instance_dataset_mid") || !tableExists(jt, "instance_dataset")) {
            return;
        }
        boolean hasConfigList = columnExists(jt, "instance_dataset", "config_list");
        String configPredicate;
        if (hasConfigList) {
            configPredicate =
                    """
                     AND (d.config_list IS NULL
                          OR JSON_TYPE(d.config_list) = 'NULL'
                          OR JSON_LENGTH(d.config_list) = 0)
                    """;
        } else {
            configPredicate = "";
        }
        String sql =
                """
                INSERT INTO instance_dataset_mid (
                    father_name, name, sensor_type, target_type, img_num, anno_num, class_num, class_list,
                    train_image_path, train_anno_path, test_image_path, test_anno_path, data_format, username, created_time, updated_time)
                SELECT d.father_name, d.name, d.sensor_type, d.target_type, d.img_num, d.anno_num, d.class_num, d.class_list,
                    d.train_image_path, d.train_anno_path, d.test_image_path, d.test_anno_path, d.data_format, d.username, d.created_time, d.updated_time
                FROM instance_dataset d
                WHERE d.train_image_path IS NOT NULL AND TRIM(d.train_image_path) <> ''
                """
                        + configPredicate
                        + """
                 AND NOT EXISTS (
                    SELECT 1 FROM instance_dataset_mid m WHERE m.train_image_path <=> d.train_image_path
                )
                """;
        int n = jt.update(sql);
        if (n > 0) {
            log.info("[schema-compat] 已从 instance_dataset 回填 {} 条到 instance_dataset_mid", n);
        }
    }

    private static void addColumnIfMissing(JdbcTemplate jt, String table, String column, String alterFragment) {
        if (!columnExists(jt, table, column)) {
            log.info("[schema-compat] 表 {} 增加列 {}", table, column);
            jt.execute("ALTER TABLE `" + table + "` " + alterFragment);
        }
    }

    private static boolean tableExists(JdbcTemplate jt, String tableName) {
        try {
            Integer cnt =
                    jt.queryForObject(
                            "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                            Integer.class,
                            tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean columnExists(JdbcTemplate jt, String tableName, String columnName) {
        try {
            Integer cnt =
                    jt.queryForObject(
                            "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                            Integer.class,
                            tableName,
                            columnName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
