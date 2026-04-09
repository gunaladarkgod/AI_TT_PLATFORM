
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_task` 
DROP COLUMN  `a_light_src`,
DROP COLUMN `a_vender`,
DROP COLUMN `a_img_size`,
DROP COLUMN `a_place`,
DROP COLUMN `a_obtained_time`;

SET FOREIGN_KEY_CHECKS = 1;
