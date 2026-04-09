
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `engine_task` ADD COLUMN `a_s` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_r` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_t` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_v` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_p` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_e` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_a` varchar(32);
ALTER TABLE  `engine_task` ADD COLUMN `a_n` varchar(64);

SET FOREIGN_KEY_CHECKS = 1;
