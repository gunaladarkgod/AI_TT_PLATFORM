SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `train_result`;
create table train_result
(
    id           int auto_increment comment '主键'
        primary key,
    task_id      int          not null comment '任务id',
    task_name    varchar(255) not null comment '任务名',
    user_name    varchar(255) not null comment '创建任务的用户名',
    model_type   varchar(50)  not null comment '模型类型（mmdet/yolov5）',
    dataset      varchar(255) null comment '数据集',
    finish_time  datetime     not null comment '任务结束时间',
    mAP          double       null comment 'coco指标',
    AP50         double       null comment 'coco指标',
    AP75         double       null comment 'coco指标',
    APs          double       null comment 'coco指标',
    APm          double       null comment 'coco指标',
    APl          double       null comment 'coco指标',
    network_name varchar(255) null
)
    comment '训练结果表';

create index idx_task_id
    on train_result (task_id);

create index idx_time
    on train_result (finish_time);

create index idx_train_result_task
    on train_result (task_id);

create index idx_train_result_user
    on train_result (user_name);

create index idx_user_name
    on train_result (user_name);

SET FOREIGN_KEY_CHECKS = 1;