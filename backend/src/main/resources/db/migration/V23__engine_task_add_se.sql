
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_task` ADD COLUMN `a_se` varchar(10);

SET FOREIGN_KEY_CHECKS = 1;
