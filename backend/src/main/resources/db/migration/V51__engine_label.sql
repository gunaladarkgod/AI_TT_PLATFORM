SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `anno_info`;
create table anno_info
(
    id           bigint unsigned auto_increment,
    project_id   bigint unsigned                            not null comment '所属项目id',
    task_id      bigint unsigned                            null,
    image_id     bigint unsigned                            not null,
    img_name     varchar(512)                               not null,
    data_stage   tinyint unsigned default '0'               not null comment '0-原始;1-任务',
    class_name   varchar(128)                               not null comment '类别名称',
    label_id     int unsigned     default '0'               not null,
    x1           double                                     null,
    y1           double                                     null,
    x2           double                                     null,
    y2           double                                     null,
    x3           double                                     null,
    y3           double                                     null,
    x4           double                                     null,
    y4           double                                     null,
    created_time datetime         default CURRENT_TIMESTAMP not null,
    primary key (id, project_id),
    constraint uq_dota_box
        unique (project_id, image_id, label_id, x1, y1, x2, y2, x3, y3, x4, y4),
    constraint chk_stage_anno
        check (`data_stage` in (0, 1))
)
    partition by hash (`project_id`) partitions 16;

create index idx_label
    on anno_info (project_id, label_id);

create index idx_task_img
    on anno_info (project_id, task_id, image_id);

SET FOREIGN_KEY_CHECKS = 1;