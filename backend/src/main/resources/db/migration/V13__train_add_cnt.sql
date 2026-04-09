
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_task` ADD COLUMN `cls_num` INTEGER;
ALTER TABLE `train_task` ADD COLUMN `prj_num` INTEGER;
ALTER TABLE `train_task` ADD COLUMN `task_num` INTEGER;
ALTER TABLE `train_task` ADD COLUMN `img_num` INTEGER;
ALTER TABLE `train_task` ADD COLUMN `obj_num` BIGINT;

SET FOREIGN_KEY_CHECKS = 1;
