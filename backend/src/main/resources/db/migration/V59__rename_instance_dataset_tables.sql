SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `instance_dataset`
    RENAME TO `instance_dataset_mid`;

ALTER TABLE `instance_datasetinfo`
    RENAME TO `instance_dataset`;

SET FOREIGN_KEY_CHECKS = 1;
