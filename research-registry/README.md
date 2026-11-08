# 研究基线公约与算法登记中心

`research-registry/` 是平台核心中受 Git 管理的**只读登记中心**。它描述研究方向、算法方案和兼容条件，不保存外部算法仓库源码，也不依赖数据库中的算法模板记录。

## 当前阶段

本目录当前完成“登记协议冻结”阶段：字段、样例和迁移状态已经确定，但平台后端和前端尚未读取本目录作为运行来源。现有 `research/` 仍是已接入本地研究基线/改进包的运行来源，不能因本目录出现而删除或移动。

登记为 `draft` 仅表示方案信息已记录；不表示算法源码已经迁移、训练环境可用或可以启动训练。

## 当前目录

```text
research-registry/
├─ registry.yaml                     # 固定枚举、目录入口和协议版本
├─ directions/<direction-id>.yaml    # 研究方向登记
├─ algorithms/<algorithm-id>.yaml    # 一个基线或改进方案的登记
└─ templates/external-algorithm.yaml # 独立算法仓库登记模板
```

当前目录服务于旧 `research/` 向多仓库架构过渡，因此已有记录仍可能使用 `baseline` 或 `improvement`。它们描述的是现有项目内实现，不应被误认为已经完成外部 B/C 仓库迁移。

## 目标目录与内容边界

完成迁移后，本目录将只保存 A 平台的公约和 C 的索引，建议扩展为：

```text
research-registry/
├─ registry.yaml                         # Schema、允许枚举、兼容性与目录入口
├─ directions/                           # 方向级公共定义
│  └─ <direction-id>.yaml                # 格式、指标、状态、适用公约
├─ conventions/                          # 数据、评测、Runner、权限、仓库生命周期规则
├─ algorithms/                           # 每份文件代表一个独立完整的 C
│  └─ <direction>--<algorithm>.yaml      # 仓库、Tag/提交、引擎、格式、B 来源
└─ templates/                            # 注册 C、algorithm.yaml、derived-from.yaml 模板
```

它不保存 B/C 的源码、数据集、权重、训练日志、虚拟环境或任何 Git 凭据。实际 C 代码位于其独立私有仓库；被平台管理员下载后，位于本机 `local-components/algorithms/`，该路径受 `.gitignore` 忽略。

未来的新 C 登记会使用 `kind: standalone-algorithm`，并增加 `derived_from`（B 的仓库、Tag 与提交）。旧字段和记录在兼容期继续可读，具体迁移步骤见 [开发者 A 任务书](../docs/v1.0/多仓库改造-开发者A-平台登记与受控算法下载.md)。

## 算法登记协议 v1

每个 `algorithms/*.yaml` 必须是一个独立方案记录。迁移期的项目内方案可以是 `baseline` 或 `improvement`；新的独立 C 算法固定使用 `standalone-algorithm`。一次调参、一次训练任务或一次结果记录不能新建算法登记。

最小字段如下：

```yaml
schema_version: 1
id: small-object/yolo11-baseline
name: YOLO11 小目标检测基线
kind: standalone-algorithm
direction: small-object
status: draft
engine:
  id: ultralytics
  version: 8.4.115
data_format: yolo
source:
  type: external-git
  repository: https://github.com/<organization>/AI_TT_ALGO_SMALL_OBJECT_YOLO11_BASELINE.git
  default_ref: develop
  stable_ref: null
  manifest: algorithm.yaml
  derived_manifest: derived-from.yaml
derived_from:
  baseline:
    repository: https://github.com/<organization>/AI_TT_BASELINE_SMALL_OBJECT_YOLO11.git
    ref: v1.0.0
    commit: <40 位提交 ID>
platform:
  min_version: null
description: 说明方案用途、边界和验证状态。
```

### 字段规则

| 字段 | 规则 |
| --- | --- |
| `schema_version` | 当前固定为整数 `1`。不兼容变更必须升级版本。 |
| `id` | 全局唯一，格式为 `方向/方案简称`；仅允许小写字母、数字和连字符及分隔符 `/`。改进包可使用 `方向/基线/改进`。 |
| `kind` | 迁移期允许 `baseline` 或 `improvement`；新外部 C 固定为 `standalone-algorithm`。 |
| `direction` | 必须存在于 `directions/`。 |
| `status` | 只允许 `draft`、`review`、`published`、`deprecated`、`archived`。只有实际验证通过且完成审查的方案才可标为 `published`。 |
| `engine.id` | 当前只允许 `mmdet`、`ultralytics`、`custom`、`paper`。`paper` 当前不代表可运行。 |
| `data_format` | 当前只允许 `coco`、`yolo`、`custom`。 |
| `source` | 指向项目内过渡来源或独立 Git 算法仓库。登记信息只用于描述和后续受控校验，浏览器与后端不得据此执行任意 Git 命令。 |
| `source.derived_manifest` | 新外部 C 的 `derived-from.yaml` 相对路径；平台下载后读取并与登记来源逐字段核对。 |
| `derived_from.baseline` | 新外部 C 的 B 来源，必须有 `repository`、`ref` 和 `commit`。 |
| `platform.min_version` | 最低兼容的平台 Release Tag；未形成稳定版本时可为 `null`，但不得假装已验证兼容。 |
| `parent` | `kind: improvement` 时必填，值为父基线的完整 `id`。 |

### 来源类型

过渡期、仍位于本项目 `research/` 中的方案使用：

```yaml
source:
  type: in-tree
  path: research/small-object/ultralytics-yolo11n-v1
  manifest: algorithm.yaml
```

独立算法仓库使用 `external-git`，必须同时声明 `repository`、`default_ref`、`stable_ref` 和 `manifest`。其中 `stable_ref` 优先使用 Release Tag 或完整提交；尚未发布稳定版本时可以为 `null`。

所有 `path`、`manifest`、训练入口等相对路径都不得包含 `..`、盘符或绝对路径。仓库地址中不得保存访问令牌、账号密码或 SSH 私钥。

## 与独立算法仓库的关系

独立算法仓库根目录的 `algorithm.yaml` 是算法实现自身的清单；本目录的登记记录是平台侧索引。两者的 `id`、`engine.id`、`engine.version`、`data_format` 必须一致。后续平台接入时，发现不一致应拒绝训练并指出字段差异。

平台侧登记不授予源码访问权限。GitHub 独立仓库的读取/写入权限仍由仓库团队、分支保护和 PR 审查管理。

## 后续阶段

1. 后端只读加载与校验本目录；
2. 前端新增“研究基线公约”展示页面；
3. 以 YOLO11 小目标基线验证独立算法仓库接入；
4. 完成真实训练验证后，再迁移其他基线和改进方案。
