
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_task` ADD COLUMN `first_img` varchar(100);

SET FOREIGN_KEY_CHECKS = 1;
