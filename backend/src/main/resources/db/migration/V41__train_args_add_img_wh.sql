
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_args` ADD COLUMN `img_w` int default 0;
ALTER TABLE  `train_args` ADD COLUMN `img_h` int default 0;

SET FOREIGN_KEY_CHECKS = 1;
