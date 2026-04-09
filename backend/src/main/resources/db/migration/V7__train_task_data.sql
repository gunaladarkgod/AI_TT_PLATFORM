/*
 Navicat Premium Dump SQL

 Source Server         : localhost_3307
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39)
 Source Host           : localhost:3307
 Source Schema         : ai_zm

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39)
 File Encoding         : 65001

 Date: 19/10/2024 21:12:06
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for train_task_data
-- ----------------------------
DROP TABLE IF EXISTS `train_task_data`;
CREATE TABLE `train_task_data`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` int NOT NULL COMMENT '训练任务id',
  `task_id` int NOT NULL COMMENT '包含的taskid',
  `project_id` int NOT NULL COMMENT '所属的projecid',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_train_pro_task`(`train_id` ASC, `project_id` ASC, `task_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
