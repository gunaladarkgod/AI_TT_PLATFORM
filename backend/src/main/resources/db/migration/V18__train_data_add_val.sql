
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_data` ADD COLUMN `val` mediumtext;

SET FOREIGN_KEY_CHECKS = 1;
