
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_task` ADD COLUMN `size` int;
UPDATE engine_task set size = segment_size;

SET FOREIGN_KEY_CHECKS = 1;
