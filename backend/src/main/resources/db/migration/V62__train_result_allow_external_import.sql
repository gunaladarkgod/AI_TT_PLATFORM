-- Imported result packages have no platform training task.
ALTER TABLE `train_result` MODIFY COLUMN `task_id` INT NULL COMMENT '任务id；外部导入结果为空';
