# 研究基线公约与算法登记中心

`research-registry/` 是平台核心中受 Git 管理的**只读登记中心**。它描述研究方向、算法方案和兼容条件，不保存外部算法仓库源码，也不依赖数据库中的算法模板记录。

## 当前阶段

本目录当前完成“登记协议冻结”阶段：字段、样例和迁移状态已经确定，但平台后端和前端尚未读取本目录作为运行来源。现有 `research/` 仍是已接入本地研究基线/改进包的运行来源，不能因本目录出现而删除或移动。

登记为 `draft` 仅表示方案信息已记录；不表示算法源码已经迁移、训练环境可用或可以启动训练。

## 目录

```text
research-registry/
├─ registry.yaml                     # 固定枚举、目录入口和协议版本
├─ directions/<direction-id>.yaml    # 研究方向登记
├─ algorithms/<algorithm-id>.yaml    # 一个基线或改进方案的登记
└─ templates/external-algorithm.yaml # 独立算法仓库登记模板
```

## 算法登记协议 v1

每个 `algorithms/*.yaml` 必须是一个独立方案记录。一个方案可以是 `baseline` 或 `improvement`；一次调参、一次训练任务或一次结果记录不能新建算法登记。

最小字段如下：

```yaml
schema_version: 1
id: small-object/yolo11-baseline
name: YOLO11 小目标检测基线
kind: baseline
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
platform:
  min_version: null
description: 说明方案用途、边界和验证状态。
```

### 字段规则

| 字段 | 规则 |
| --- | --- |
| `schema_version` | 当前固定为整数 `1`。不兼容变更必须升级版本。 |
| `id` | 全局唯一，格式为 `方向/方案简称`；仅允许小写字母、数字和连字符及分隔符 `/`。改进包可使用 `方向/基线/改进`。 |
| `kind` | 只允许 `baseline` 或 `improvement`。 |
| `direction` | 必须存在于 `directions/`。 |
| `status` | 只允许 `draft`、`review`、`published`、`deprecated`、`archived`。只有实际验证通过且完成审查的方案才可标为 `published`。 |
| `engine.id` | 当前只允许 `mmdet`、`ultralytics`、`custom`、`paper`。`paper` 当前不代表可运行。 |
| `data_format` | 当前只允许 `coco`、`yolo`、`custom`。 |
| `source` | 指向项目内过渡来源或独立 Git 算法仓库。登记信息只用于描述和后续受控校验，浏览器与后端不得据此执行任意 Git 命令。 |
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
