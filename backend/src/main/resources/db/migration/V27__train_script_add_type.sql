
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_script` ADD COLUMN `type` varchar(20);

UPDATE `train_script` set type='train' WHERE type is NULL;

ALTER TABLE `train_script` ADD INDEX idx_type_script (type);

SET FOREIGN_KEY_CHECKS = 1;
