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

 Date: 23/10/2024 09:33:40
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for train_args
-- ----------------------------
DROP TABLE IF EXISTS `train_args`;
CREATE TABLE `train_args`  (
  `id` bigint NOT NULL,
  `weights` int NULL DEFAULT NULL COMMENT '初始权重\r\n',
  `hyp` int NULL DEFAULT NULL COMMENT '超参配置',
  `cfg` int NULL DEFAULT NULL COMMENT '模型配置文件',
  `batch_size` int UNSIGNED NULL DEFAULT 16 COMMENT '16',
  `node` int NULL DEFAULT NULL COMMENT 'nproc_per_node',
  `device` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '0,1,2,3 or cpu',
  `img_size` int UNSIGNED NULL DEFAULT 640 COMMENT '640',
  `epoch` int UNSIGNED NULL DEFAULT 100 COMMENT '训练轮次',
  `period` int NULL DEFAULT NULL COMMENT 'save-period  权重保存间隔,-1不保存',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
