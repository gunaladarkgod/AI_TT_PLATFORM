# 算法登记字段公约

每个登记文件至少包含 `schema_version`、`id`、`name`、`direction`、`status`、`repository`、`platform`、`engine`、`data_format`、`algorithm_manifest` 和 `description`。`id` 使用 `方向/方案简称`，仅允许小写字母、数字和连字符；仓库地址仅用于展示，必须是 HTTPS 地址。`status` 可取 `draft`、`review`、`published`，其中草稿和评审中方案不得被表述为已接入训练。
