SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `template_mapping`;
CREATE TABLE IF NOT EXISTS template_mapping (
                                                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                                name VARCHAR(100) NOT NULL COMMENT '模板名称，格式：词语1_词语2_词语3',
    class_list JSON NOT NULL COMMENT '类别列表',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签映射模板表';

SET FOREIGN_KEY_CHECKS = 1;