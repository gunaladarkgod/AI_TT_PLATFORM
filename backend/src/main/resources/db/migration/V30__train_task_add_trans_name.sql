
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_task` ADD COLUMN `trans_name` varchar(64);

SET FOREIGN_KEY_CHECKS = 1;
