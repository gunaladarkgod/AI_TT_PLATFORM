package com.xgls.web.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Apply the V62 compatibility change even when Flyway is disabled. */
@Component
@Slf4j
@Order(2)
public class TrainResultSchemaCompatibilityRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public TrainResultSchemaCompatibilityRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer required = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'train_result'
                      AND column_name = 'task_id' AND is_nullable = 'NO'
                    """, Integer.class);
            if (required != null && required > 0) {
                jdbc.execute("ALTER TABLE `train_result` MODIFY COLUMN `task_id` INT NULL COMMENT '任务id；外部导入结果为空'");
                log.info("[schema-compat] train_result.task_id 已允许为空，支持导入外部结果包");
            }
        } catch (Exception error) {
            throw new IllegalStateException("训练结果表结构兼容处理失败：请检查数据库 ALTER 权限，"
                    + "或启用 APP_FLYWAY_ENABLED=true、APP_FLYWAY_MODE=migrate 应用 V62 迁移后重启后端", error);
        }
    }
}
