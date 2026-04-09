
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_project` ADD COLUMN `a_l` varchar(32);
ALTER TABLE  `engine_project` ADD COLUMN `a_s` varchar(32);
ALTER TABLE  `engine_project` ADD COLUMN `a_n` varchar(64);

SET FOREIGN_KEY_CHECKS = 1;
