
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE  `train_task` ADD COLUMN `username` varchar(100);
CREATE INDEX idx_username_task ON train_task (username);
update train_task SET username = 'admin' WHERE username is null;

SET FOREIGN_KEY_CHECKS = 1;
