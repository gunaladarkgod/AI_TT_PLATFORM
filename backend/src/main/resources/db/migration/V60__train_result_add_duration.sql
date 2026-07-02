SET NAMES utf8mb4;

SET @duration_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'train_result'
      AND COLUMN_NAME = 'duration_seconds'
);
SET @duration_column_sql = IF(
    @duration_column_exists = 0,
    'ALTER TABLE `train_result` ADD COLUMN `duration_seconds` BIGINT NULL COMMENT ''本次训练耗时（秒）'' AFTER `finish_time`',
    'SELECT 1'
);
PREPARE duration_column_stmt FROM @duration_column_sql;
EXECUTE duration_column_stmt;
DEALLOCATE PREPARE duration_column_stmt;
