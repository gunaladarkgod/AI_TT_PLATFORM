SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `img_info`;
create table img_info
(
    id         bigint unsigned auto_increment,
    project_id bigint unsigned              not null comment '所属项目id',
    task_id    bigint unsigned              null,
    img_name   varchar(512)                 not null,
    data_stage tinyint unsigned default '0' not null comment '0-原始;1-任务',
    width      int unsigned                 not null,
    height     int unsigned                 not null,
    primary key (id, project_id),
    constraint uq_proj_imgname
        unique (project_id, img_name)
)
    partition by hash (`project_id`) partitions 16;

create index idx_task_project
    on img_info (task_id, project_id);


SET FOREIGN_KEY_CHECKS = 1;