
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_script` ADD COLUMN `val` varchar(255) default NULL;
ALTER TABLE  `train_script` ADD COLUMN `detect` varchar(255) default NULL;

SET FOREIGN_KEY_CHECKS = 1;
