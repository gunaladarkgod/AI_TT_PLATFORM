SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `instance_datasetinfo`;
create table instance_datasetinfo
(
    id               bigint unsigned auto_increment
        primary key,
    father_name      varchar(255)                               not null comment '源数据集名称（原任务数据集名称）',
    name             varchar(255)                               not null comment '实例数据集名称',
    sensor_type      varchar(15)                                null comment '传感器类型',
    target_type      varchar(15)                                null comment '目标类型',
    img_num          int unsigned     default '0'               not null comment '图片数量',
    anno_num         int unsigned     default '0'               not null comment '标注数量',
    class_num        int unsigned     default '0'               null comment '类别数量',
    class_list       json                                       null comment '类别列表',
    train_image_path varchar(1024)                              null comment '训练数据路径',
    train_anno_path  varchar(1024)                              null comment '训练标注路径',
    test_image_path  varchar(1024)                              null comment '测试数据路径',
    test_anno_path   varchar(1024)                              null comment '测试标注路径',
    data_format      tinyint unsigned default '0'               not null comment '数据格式（0-Platform；1-CVAT; 2-COCO）',
    username         varchar(64)                                null comment '创建人',
    created_time     datetime         default CURRENT_TIMESTAMP not null,
    updated_time     datetime         default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    config_list      json                                       null comment '预处理链路',
    param_schema     json                                       null comment '实际参数'
);

SET FOREIGN_KEY_CHECKS = 1;