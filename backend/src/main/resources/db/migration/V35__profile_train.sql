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

 Date: 09/04/2025 11:46:31
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for profile_train
-- ----------------------------
DROP TABLE IF EXISTS `profile_train`;
CREATE TABLE `profile_train`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板名称',
  `alg_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '算法id',
  `weights` int NULL DEFAULT NULL COMMENT '初始权重\r\n',
  `hyp` int NULL DEFAULT NULL COMMENT '超参配置',
  `cfg` int NULL DEFAULT NULL COMMENT '模型配置文件',
  `batch_size` int UNSIGNED NULL DEFAULT 16 COMMENT '16',
  `device` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '0,1,2,3 or cpu',
  `img_size` int NULL DEFAULT NULL COMMENT '640',
  `epoch` int UNSIGNED NULL DEFAULT NULL COMMENT '训练轮次',
  `period` int NULL DEFAULT NULL COMMENT 'save-period  权重保存间隔,-1不保存',
  `val_ratio` int NULL DEFAULT NULL COMMENT '自训自测比例',
  `f_max` int NULL DEFAULT 0 COMMENT '过滤,长边',
  `f_min` int NULL DEFAULT 0 COMMENT '过滤,短边',
  `f_area` int NULL DEFAULT 0 COMMENT '过滤,面积',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
