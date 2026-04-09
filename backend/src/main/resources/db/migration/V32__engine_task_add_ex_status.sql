
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_task` ADD COLUMN `export_status` integer default 0;

ALTER TABLE  `engine_task` ADD COLUMN `export_type` integer default 0;
ALTER TABLE  `engine_task` ADD COLUMN `export_queue` bigint default 0;

SET FOREIGN_KEY_CHECKS = 1;
