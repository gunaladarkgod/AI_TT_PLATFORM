
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE  `sys_menu`;

INSERT INTO `sys_menu` VALUES (1, 'taskDatasetbaseManage', '任务数据集管理', 1, 0, 0);
INSERT INTO `sys_menu` VALUES (2, 'engineProject', '任务数据集子集划分', 2, 0, 0);
INSERT INTO `sys_menu` VALUES (3, 'taskDatasetMerge', '任务数据集标签生成', 3, 0, 0);
INSERT INTO `sys_menu` VALUES (4, 'instanceDatabaseManage', '实例数据集管理', 4, 0, 0);
INSERT INTO `sys_menu` VALUES (5, 'traintestsplit', '实例数据集训测划分', 5, 0, 0);
INSERT INTO `sys_menu` VALUES (6, 'preprocess', '实例数据集预处理', 6, 0, 0);
INSERT INTO `sys_menu` VALUES (7, 'trainTask', '模型训练', 7, 0, 0);
INSERT INTO `sys_menu` VALUES (8, 'resultQuery', '结果查询', 8, 0, 0);
INSERT INTO `sys_menu` VALUES (9, 'modelTrans', '模型转换', 9, 0, 0);
INSERT INTO `sys_menu` VALUES (10, 'trainYolo', '配置文件', 10, 0, 0);
INSERT INTO `sys_menu` VALUES (11, 'trainScript', '算法管理', 11, 0, 15);
INSERT INTO `sys_menu` VALUES (12, 'algorithmTemplate', '算法模板', 12, 0, 15);
INSERT INTO `sys_menu` VALUES (13, 'user', '用户管理', 13, 0, 15);
INSERT INTO `sys_menu` VALUES (14, 'menu', '菜单管理', 14, 0, 15);
INSERT INTO `sys_menu` VALUES (15, 'other', '其他', 15, 0, 0);

SET FOREIGN_KEY_CHECKS = 1;
