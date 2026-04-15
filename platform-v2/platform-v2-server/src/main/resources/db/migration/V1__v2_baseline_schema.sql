-- AI Platform V2 — baseline schema (new design, not compatible with legacy ai_zm_master)
-- Conventions: snake_case, BIGINT PKs, explicit FKs, soft-delete where sync entities need history

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- Identity & RBAC (replaces scattered sys_user / sys_menu / sys_role_menu)
-- ---------------------------------------------------------------------------
CREATE TABLE usr_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(32)     NOT NULL COMMENT 'ADMIN, USER, ...',
    name        VARCHAR(64)     NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usr_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usr_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100)    NOT NULL,
    password_hash   VARCHAR(128)    NOT NULL COMMENT 'BCrypt or similar; do not store MD5 in new system',
    nickname        VARCHAR(100)    NULL,
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '0 locked, 1 active',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usr_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE usr_user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_uur_user FOREIGN KEY (user_id) REFERENCES usr_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_uur_role FOREIGN KEY (role_id) REFERENCES usr_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE menu_item (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL COMMENT 'stable key for frontend i18n / route',
    name        VARCHAR(128) NOT NULL,
    path        VARCHAR(128) NULL,
    parent_id   INT UNSIGNED NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    visible     TINYINT      NOT NULL DEFAULT 1,
    UNIQUE KEY uk_menu_item_code (code),
    CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id) REFERENCES menu_item (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_menu (
    role_id BIGINT UNSIGNED NOT NULL,
    menu_id INT UNSIGNED    NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    CONSTRAINT fk_rm_role FOREIGN KEY (role_id) REFERENCES usr_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_rm_menu FOREIGN KEY (menu_id) REFERENCES menu_item (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Engine / CVAT mirror (annotation domain)
-- ---------------------------------------------------------------------------
CREATE TABLE eng_project (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    external_id     BIGINT UNSIGNED NULL COMMENT 'remote (e.g. CVAT) project id',
    name            VARCHAR(255)    NULL,
    bug_tracker     VARCHAR(2000)   NULL,
    status          VARCHAR(32)     NULL,
    owner_ext_id    BIGINT UNSIGNED NULL,
    assignee_ext_id BIGINT UNSIGNED NULL,
    organization_ext_id BIGINT UNSIGNED NULL,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    deleted_at      DATETIME        NULL,
    created_at      DATETIME        NULL,
    updated_at      DATETIME        NULL,
    KEY idx_eng_project_ext (external_id),
    KEY idx_eng_project_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eng_task (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT UNSIGNED NOT NULL,
    external_id     BIGINT UNSIGNED NULL,
    name            VARCHAR(255)    NULL,
    mode            VARCHAR(32)     NULL,
    status          VARCHAR(32)     NULL,
    subset          VARCHAR(64)     NULL,
    dimension       VARCHAR(8)      NULL,
    export_status   VARCHAR(32)     NULL,
    export_label    VARCHAR(255)    NULL,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NULL,
    updated_at      DATETIME        NULL,
    CONSTRAINT fk_eng_task_proj FOREIGN KEY (project_id) REFERENCES eng_project (id) ON DELETE CASCADE,
    KEY idx_eng_task_proj (project_id),
    KEY idx_eng_task_ext (external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE eng_label (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT UNSIGNED NOT NULL,
    task_id         BIGINT UNSIGNED NOT NULL,
    external_id     BIGINT UNSIGNED NULL,
    name            VARCHAR(128)    NULL,
    color           VARCHAR(16)     NULL,
    label_type      VARCHAR(32)     NULL,
    parent_id       BIGINT UNSIGNED NULL,
    CONSTRAINT fk_eng_label_proj FOREIGN KEY (project_id) REFERENCES eng_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_eng_label_task FOREIGN KEY (task_id) REFERENCES eng_task (id) ON DELETE CASCADE,
    KEY idx_eng_label_task (task_id),
    KEY idx_eng_label_proj_name (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Which annotation projects a user may access (after eng_project exists)
CREATE TABLE usr_project_access (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT UNSIGNED NOT NULL,
    annotation_project_id   BIGINT UNSIGNED NOT NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_upa_user_proj (user_id, annotation_project_id),
    CONSTRAINT fk_upa_user FOREIGN KEY (user_id) REFERENCES usr_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_upa_proj FOREIGN KEY (annotation_project_id) REFERENCES eng_project (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Dataset pipeline (original → task-level → instance + snapshots)
-- ---------------------------------------------------------------------------
CREATE TABLE dat_original_dataset (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT UNSIGNED NOT NULL,
    task_ext_id     VARCHAR(255)    NULL COMMENT 'remote task id string if composite',
    name            VARCHAR(255)    NOT NULL,
    data_format     VARCHAR(16)     NOT NULL COMMENT 'PLATFORM, CVAT, COCO',
    type_mark       VARCHAR(16)     NOT NULL DEFAULT 'TASK_TARGET' COMMENT 'PRETRAIN_SUBSET, TASK_TARGET, BOTH',
    img_count       INT UNSIGNED    NOT NULL DEFAULT 0,
    anno_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    class_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    class_list_json JSON           NULL,
    data_path       VARCHAR(1024)   NULL,
    anno_path       VARCHAR(1024)   NULL,
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dod_proj FOREIGN KEY (project_id) REFERENCES eng_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_dod_user FOREIGN KEY (created_by) REFERENCES usr_user (id) ON DELETE SET NULL,
    KEY idx_dod_proj (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dat_task_dataset (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(255)    NOT NULL,
    data_format         VARCHAR(16)     NOT NULL,
    core_subset_json    JSON            NOT NULL COMMENT 'ids, names, paths, counts for target subset',
    sup_subset_json     JSON            NOT NULL COMMENT 'pretrain subset',
    created_by          BIGINT UNSIGNED NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dtd_user FOREIGN KEY (created_by) REFERENCES usr_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dat_instance_dataset (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_task_ds_id   BIGINT UNSIGNED NULL COMMENT 'optional FK to dat_task_dataset',
    name                VARCHAR(255)    NOT NULL,
    father_name         VARCHAR(255)    NULL COMMENT 'legacy: parent task dataset name',
    data_format         VARCHAR(16)     NOT NULL,
    img_count           INT UNSIGNED    NOT NULL DEFAULT 0,
    class_list_json     JSON            NULL,
    created_by          BIGINT UNSIGNED NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_did_std FOREIGN KEY (source_task_ds_id) REFERENCES dat_task_dataset (id) ON DELETE SET NULL,
    CONSTRAINT fk_did_user FOREIGN KEY (created_by) REFERENCES usr_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One row per preprocess / split result (replaces loose instance_datasetinfo without version clarity)
CREATE TABLE dat_instance_snapshot (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    instance_dataset_id     BIGINT UNSIGNED NOT NULL,
    version                 INT UNSIGNED    NOT NULL DEFAULT 1,
    preprocess_pipeline_json JSON         NULL,
    param_schema_json       JSON            NULL,
    train_image_path        VARCHAR(1024)   NULL,
    train_anno_path         VARCHAR(1024)   NULL,
    test_image_path         VARCHAR(1024)   NULL,
    test_anno_path          VARCHAR(1024)   NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dis_inst FOREIGN KEY (instance_dataset_id) REFERENCES dat_instance_dataset (id) ON DELETE CASCADE,
    UNIQUE KEY uk_dis_inst_ver (instance_dataset_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dat_image (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT UNSIGNED NOT NULL,
    task_id         BIGINT UNSIGNED NULL,
    stage           VARCHAR(16)     NOT NULL COMMENT 'ORIGINAL, TASK',
    file_name       VARCHAR(512)    NOT NULL,
    width           INT UNSIGNED    NOT NULL,
    height          INT UNSIGNED    NOT NULL,
    CONSTRAINT fk_dim_proj FOREIGN KEY (project_id) REFERENCES eng_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_dim_task FOREIGN KEY (task_id) REFERENCES eng_task (id) ON DELETE SET NULL,
    UNIQUE KEY uk_dim_proj_file (project_id, file_name),
    KEY idx_dim_task (task_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dat_annotation (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT UNSIGNED NOT NULL,
    task_id         BIGINT UNSIGNED NULL,
    image_id        BIGINT UNSIGNED NOT NULL,
    label_id        BIGINT UNSIGNED NOT NULL,
    class_name      VARCHAR(128)    NOT NULL,
    stage           VARCHAR(16)     NOT NULL,
    x1 DOUBLE NULL, y1 DOUBLE NULL, x2 DOUBLE NULL, y2 DOUBLE NULL,
    x3 DOUBLE NULL, y3 DOUBLE NULL, x4 DOUBLE NULL, y4 DOUBLE NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dan_proj FOREIGN KEY (project_id) REFERENCES eng_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_dan_task FOREIGN KEY (task_id) REFERENCES eng_task (id) ON DELETE SET NULL,
    CONSTRAINT fk_dan_img FOREIGN KEY (image_id) REFERENCES dat_image (id) ON DELETE CASCADE,
    CONSTRAINT fk_dan_label FOREIGN KEY (label_id) REFERENCES eng_label (id) ON DELETE CASCADE,
    KEY idx_dan_img (project_id, image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Preprocess scripts
-- ---------------------------------------------------------------------------
CREATE TABLE preprocess_script (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    script_path     VARCHAR(500)    NOT NULL,
    script_kind     VARCHAR(16)     NOT NULL COMMENT 'AUGMENT, ENHANCE',
    param_schema_json JSON        NULL,
    uploader_id     BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ps_uploader FOREIGN KEY (uploader_id) REFERENCES usr_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Training (algorithm registry + job + normalized scope & hyperparams)
-- ---------------------------------------------------------------------------
CREATE TABLE trn_algorithm (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)     NOT NULL COMMENT 'mmdet, yolov5, pt2onnx, ...',
    category        VARCHAR(16)     NOT NULL COMMENT 'TRAIN, TRANSFORM, DATA, VAL, PREDICT',
    conda_env       VARCHAR(255)    NULL,
    launch_cmd      VARCHAR(255)    NULL,
    entry_file      VARCHAR(255)    NULL,
    script_suffix   VARCHAR(16)     NULL COMMENT '.sh / .bat',
    remark          VARCHAR(500)    NULL,
    UNIQUE KEY uk_trn_alg_code_cat (code, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trn_job (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    algorithm_id        INT UNSIGNED    NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    external_run_id     VARCHAR(128)    NULL COMMENT 'run id for Python runner',
    status              VARCHAR(32)     NOT NULL COMMENT 'DRAFT, READY, QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED',
    run_state           VARCHAR(32)     NULL COMMENT 'SUCCESS, ERROR',
    run_name            VARCHAR(128)    NULL COMMENT 'last exp folder',
    remark              VARCHAR(500)    NULL,
    cls_count           INT UNSIGNED    NULL,
    img_count           BIGINT UNSIGNED NULL,
    obj_count           BIGINT UNSIGNED NULL,
    enqueue_priority    BIGINT          NULL COMMENT 'unix ms for queue ordering',
    created_by          BIGINT UNSIGNED NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at          DATETIME        NULL,
    finished_at         DATETIME        NULL,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tj_alg FOREIGN KEY (algorithm_id) REFERENCES trn_algorithm (id),
    CONSTRAINT fk_tj_user FOREIGN KEY (created_by) REFERENCES usr_user (id) ON DELETE SET NULL,
    KEY idx_tj_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trn_job_scope (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id          BIGINT UNSIGNED NOT NULL,
    project_id      BIGINT UNSIGNED NOT NULL,
    task_id         BIGINT UNSIGNED NOT NULL,
    CONSTRAINT fk_tjs_job FOREIGN KEY (job_id) REFERENCES trn_job (id) ON DELETE CASCADE,
    CONSTRAINT fk_tjs_proj FOREIGN KEY (project_id) REFERENCES eng_project (id) ON DELETE CASCADE,
    CONSTRAINT fk_tjs_task FOREIGN KEY (task_id) REFERENCES eng_task (id) ON DELETE CASCADE,
    UNIQUE KEY uk_tjs (job_id, project_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trn_job_label (
    job_id      BIGINT UNSIGNED NOT NULL,
    label_id    BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (job_id, label_id),
    CONSTRAINT fk_tjl_job FOREIGN KEY (job_id) REFERENCES trn_job (id) ON DELETE CASCADE,
    CONSTRAINT fk_tjl_label FOREIGN KEY (label_id) REFERENCES eng_label (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trn_job_config (
    job_id              BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    batch_size          INT UNSIGNED    NULL,
    epoch               INT UNSIGNED    NULL,
    device              VARCHAR(255)    NULL,
    img_size            INT UNSIGNED    NULL,
    save_period         INT             NULL,
    weight_file_id      INT UNSIGNED    NULL COMMENT 'optional FK to file registry later',
    hyp_file_id         INT UNSIGNED    NULL,
    cfg_file_id         INT UNSIGNED    NULL,
    extra_json          JSON            NULL,
    CONSTRAINT fk_tjc_job FOREIGN KEY (job_id) REFERENCES trn_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trn_job_extra (
    job_id      BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    payload_json JSON         NULL,
    aux_file    VARCHAR(255)    NULL,
    updated_at  DATETIME        NULL,
    CONSTRAINT fk_tje_job FOREIGN KEY (job_id) REFERENCES trn_job (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trn_result (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id          BIGINT UNSIGNED NOT NULL,
    model_type      VARCHAR(64)     NOT NULL,
    dataset_label   VARCHAR(255)    NULL,
    finished_at     DATETIME        NOT NULL,
    map_score       DOUBLE          NULL,
    ap50            DOUBLE          NULL,
    ap75            DOUBLE          NULL,
    aps             DOUBLE          NULL,
    apm             DOUBLE          NULL,
    apl             DOUBLE          NULL,
    network_name    VARCHAR(255)    NULL,
    CONSTRAINT fk_tr_job FOREIGN KEY (job_id) REFERENCES trn_job (id) ON DELETE CASCADE,
    KEY idx_tr_job (job_id),
    KEY idx_tr_time (finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Model conversion
-- ---------------------------------------------------------------------------
CREATE TABLE mdl_conversion_job (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    algorithm_code  VARCHAR(32)     NOT NULL COMMENT 'pt2onnx, pt2rknn, ...',
    status          VARCHAR(32)     NOT NULL,
    params_json     JSON            NULL,
    weight_file_name VARCHAR(255)   NULL,
    source_job_id   BIGINT UNSIGNED NULL COMMENT 'optional trn_job that produced weights',
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      DATETIME        NULL,
    finished_at     DATETIME        NULL,
    remark          VARCHAR(500)    NULL,
    CONSTRAINT fk_mdl_src FOREIGN KEY (source_job_id) REFERENCES trn_job (id) ON DELETE SET NULL,
    CONSTRAINT fk_mdl_user FOREIGN KEY (created_by) REFERENCES usr_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Tag mapping templates
-- ---------------------------------------------------------------------------
CREATE TABLE tmpl_label_mapping (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    class_list_json JSON            NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tlm_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profile_train (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    algorithm_id    INT UNSIGNED    NULL,
    batch_size      INT UNSIGNED    NULL,
    device          VARCHAR(255)    NULL,
    img_size        INT UNSIGNED    NULL,
    epoch           INT UNSIGNED    NULL,
    save_period     INT             NULL,
    val_ratio       INT             NULL,
    filter_max_side INT             NULL,
    filter_min_side INT             NULL,
    filter_area     INT             NULL,
    remark          VARCHAR(500)    NULL,
    CONSTRAINT fk_pt_alg FOREIGN KEY (algorithm_id) REFERENCES trn_algorithm (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profile_trans (
    id              INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    algorithm_code  VARCHAR(32)     NOT NULL,
    params_json     JSON            NULL,
    remark          VARCHAR(500)    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
