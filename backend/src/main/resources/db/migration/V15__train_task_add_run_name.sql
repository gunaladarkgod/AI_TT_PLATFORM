
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_task` ADD COLUMN `run_name` varchar(64);
ALTER TABLE  `train_task` ADD COLUMN `enqueue` BIGINT;

SET FOREIGN_KEY_CHECKS = 1;
