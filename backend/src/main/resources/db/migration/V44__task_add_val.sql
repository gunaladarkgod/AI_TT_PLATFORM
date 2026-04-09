
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_task` ADD COLUMN `val_name` varchar(64) default NULL;
ALTER TABLE  `train_task` ADD COLUMN `val_state` int default 0;

SET FOREIGN_KEY_CHECKS = 1;
