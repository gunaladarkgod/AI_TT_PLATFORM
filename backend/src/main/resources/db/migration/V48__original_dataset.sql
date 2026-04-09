SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `original_dataset`;
create table original_dataset
(
    id           bigint unsigned auto_increment
        primary key,
    name         varchar(255)                               not null comment '数据集名称',
    sensor_type  varchar(15)                                null comment '传感器类型',
    target_type  varchar(15)                                null comment '目标类型',
    img_num      int unsigned     default '0'               not null comment '图片数量',
    anno_num     int unsigned     default '0'               not null comment '标注数量',
    class_num    int unsigned     default '0'               null comment '类别数量',
    class_list   json                                       null,
    data_format  tinyint unsigned default '0'               not null comment '数据格式（0-Platform；1-CVAT; 2-COCO）',
    username     varchar(64)                                null comment '创建人',
    data_path    varchar(1024)                              null comment '数据路径',
    anno_path    varchar(1024)                              null comment '标注路径',
    type_mark    tinyint unsigned default '2'               not null comment '0-预训练子集；1-任务目标子集',
    created_time datetime         default CURRENT_TIMESTAMP not null,
    project_id   bigint unsigned                            not null comment '所属项目id',
    project_name varchar(255)                               null comment '所属项目名',
    task_id      varchar(255)                               null comment '任务id',
    task_name    varchar(255)                               null comment '任务名',
    updated_time datetime         default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint chk_counts_nonneg
        check ((`img_num` >= 0) and (`anno_num` >= 0) and (`class_num` >= 0)),
    constraint chk_data_format
        check (`data_format` in (0, 1, 2)),
    constraint chk_type_mark
        check (`type_mark` in (0, 1, 2))
);


create index idx_proj_stage_fmt
    on original_dataset (project_id, data_format);

create index idx_project_task
    on original_dataset (project_id, task_id);

SET FOREIGN_KEY_CHECKS = 1;