SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `new_preprocess_scriptinfo`;
create table new_preprocess_scriptinfo
(
    id           int auto_increment comment '总脚本表的主键，唯一标识测试信息'
        primary key,
    name         varchar(255) not null comment '脚本名字',
    script_path  varchar(500) null comment '脚本所在路径，由后端计算',
    type         int          not null comment '脚本类型，0：增强 1：增广',
    param_schema text         null comment '脚本参数定义，JSON格式',
    uploader     varchar(100) null comment '脚本上传者用户名'
)
    comment '预处理脚本信息表';

SET FOREIGN_KEY_CHECKS = 1;