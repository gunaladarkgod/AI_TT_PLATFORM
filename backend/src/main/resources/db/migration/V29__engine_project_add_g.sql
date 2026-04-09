
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_project` ADD COLUMN `a_g` varchar(32);

SET FOREIGN_KEY_CHECKS = 1;
