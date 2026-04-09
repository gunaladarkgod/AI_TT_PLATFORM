
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_args` ADD COLUMN `val_ratio` integer;

SET FOREIGN_KEY_CHECKS = 1;
