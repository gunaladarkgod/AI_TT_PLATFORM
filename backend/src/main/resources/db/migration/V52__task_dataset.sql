SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `task_dataset`;
CREATE TABLE `task_dataset` (
                                `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `name`          VARCHAR(255)     NOT NULL COMMENT '数据集名称',
                                `sensor_type`   VARCHAR(15)      NULL COMMENT '传感器类型',
                                `target_type`   VARCHAR(15)      NULL COMMENT '目标类型',
                                `data_format`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '数据格式（0-Platform；1-CVAT；2-COCO）',
                                `username`      VARCHAR(64)      NULL COMMENT '创建人',
                                `created_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `core_id`    varchar(255)     NOT NULL COMMENT '目标子集中的元素id列表',
                                `core_name`  VARCHAR(255)     NULL COMMENT '目标子集中的元素名列表',
                                `core_target_type`   VARCHAR(15)      NULL COMMENT '目标子集中的目标类型',
                                `core_img_num`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '目标子集中的图片数量',
                                `core_anno_num`      INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '目标子集中的标注数量',
                                `core_class_num`     INT UNSIGNED     NULL DEFAULT 0 COMMENT '目标子集中的类别数量',
                                `core_class_list`    VARCHAR(255)     NULL COMMENT '目标子集中的类别列表',
                                `core_data_path`     VARCHAR(1024)    NULL COMMENT '目标子集中的数据路径',
                                `core_anno_path`     VARCHAR(1024)    NULL COMMENT '目标子集中的标注路径',
                                `sup_id`    varchar(255)     NOT NULL COMMENT '预训练子集中的元素id列表',
                                `sup_name`  VARCHAR(255)     NULL COMMENT '预训练子集中的元素名列表',
                                `sup_target_type`   VARCHAR(15)      NULL COMMENT '预训练子集中的目标类型',
                                `sup_img_num`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '预训练子集中的图片数量',
                                `sup_anno_num`      INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '预训练子集中的标注数量',
                                `sup_class_num`     INT UNSIGNED     NULL DEFAULT 0 COMMENT '预训练子集中的类别数量',
                                `sup_class_list`    VARCHAR(255)     NULL COMMENT '预训练子集中的类别列表',
                                `sup_data_path`     VARCHAR(1024)    NULL COMMENT '预训练子集中的数据路径',
                                `sup_anno_path`     VARCHAR(1024)    NULL COMMENT '预训练子集中的标注路径',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务数据集信息表';

SET FOREIGN_KEY_CHECKS = 1;