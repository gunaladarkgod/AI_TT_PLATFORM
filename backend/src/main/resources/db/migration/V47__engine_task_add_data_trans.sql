
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `engine_task` add COLUMN `data_trans` int DEFAULT 0;

SET FOREIGN_KEY_CHECKS = 1;
