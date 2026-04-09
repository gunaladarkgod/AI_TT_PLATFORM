
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_args` ADD COLUMN `f_max` int default 0;
ALTER TABLE  `train_args` ADD COLUMN `f_min` int default 0;
ALTER TABLE  `train_args` ADD COLUMN `f_area` int default 0;

SET FOREIGN_KEY_CHECKS = 1;
