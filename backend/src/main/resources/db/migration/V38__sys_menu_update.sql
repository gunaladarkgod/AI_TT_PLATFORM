/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80041
 Source Host           : localhost:3307
 Source Schema         : ai_zm

 Target Server Type    : MySQL
 Target Server Version : 80041
 File Encoding         : 65001

 Date: 11/04/2025 16:56:05
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `url` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'url',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `order_num` int(0) NULL DEFAULT 0 COMMENT '排序',
  `is_hidden` int(0) UNSIGNED NULL DEFAULT 0 COMMENT '是否显示',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uq_name_menu`(`url`) USING BTREE,
  UNIQUE INDEX `uq_title_menu`(`name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, 'taskDatasetbaseManage', '任务数据集管理', 1, 0);
INSERT INTO `sys_menu` VALUES (2, 'engineProject', '任务数据集子集划分', 2, 0);
INSERT INTO `sys_menu` VALUES (3, 'taskDatasetMerge', '任务数据集标签生成', 3, 0);
INSERT INTO `sys_menu` VALUES (4, 'instanceDatabaseManage', '实例数据集管理', 4, 0);
INSERT INTO `sys_menu` VALUES (5, 'traintestsplit', '实例数据集训测划分', 5, 0);
INSERT INTO `sys_menu` VALUES (6, 'preprocess', '实例数据集预处理', 6, 0);
INSERT INTO `sys_menu` VALUES (7, 'trainTask', '模型训练', 7, 0);
INSERT INTO `sys_menu` VALUES (8, 'resultQuery', '结果查询', 8, 0);
INSERT INTO `sys_menu` VALUES (9, 'modelTrans', '模型转换', 9, 0);
INSERT INTO `sys_menu` VALUES (10, 'trainYolo', '配置文件', 10, 0);
INSERT INTO `sys_menu` VALUES (11, 'trainScript', '算法管理', 11, 0);
INSERT INTO `sys_menu` VALUES (12, 'algorithmTemplate', '算法模板', 12, 0);
INSERT INTO `sys_menu` VALUES (13, 'user', '用户管理', 13, 0);
INSERT INTO `sys_menu` VALUES (14, 'menu', '菜单管理', 14, 0);
INSERT INTO `sys_menu` VALUES (15, 'other', '其他', 15, 0);

SET FOREIGN_KEY_CHECKS = 1;
