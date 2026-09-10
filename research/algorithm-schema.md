# 研究算法包协议 v1

本协议约束 `research/` 下的方向、基线和改进包。算法定义保存在项目文件中，不依赖算法模板数据库表。

## 1. 方向清单：`direction.yaml`

每个研究方向目录必须包含一个 `direction.yaml`：

```yaml
schema_version: 1
id: small-object
name: 小目标检测
description: 面向小尺度目标检测的基线与改进方法。
status: active
```

`id` 仅允许小写字母、数字和连字符；它同时是目录名。`status` 当前可取 `active`、`deprecated`、`archived`。

## 2. 基线清单：`algorithm.yaml`

基线位于 `{direction}/{baseline-id}/algorithm.yaml`：

```yaml
schema_version: 1
id: small-object/mmdet-faster-rcnn-r50-v1
kind: baseline
name: MMDet Faster R-CNN R50 小目标基线 v1
direction: small-object
engine: mmdet
engine_version: 3.0.0
status: draft
data_format: coco
entry: mmdet/config.py
source_root: src
description: 使用 MMDetection 3.0.0 的可复现小目标检测起点。
parameters: []
```

字段规则：

| 字段 | 说明 |
| --- | --- |
| `id` | 全局唯一算法 ID，格式为 `方向/算法目录名` |
| `kind` | 基线固定为 `baseline`；改进固定为 `improvement` |
| `engine` | `mmdet`、`ultralytics` 或 `paper` |
| `engine_version` | 固定运行框架版本；论文源码可填固定 Git commit 或 `locked` |
| `status` | `draft`、`review`、`published`、`deprecated`、`archived` |
| `data_format` | 当前支持 `coco`、`yolo`、`custom` |
| `entry` | 相对算法包根目录的训练配置或训练脚本 |
| `source_root` | 可选的算法自定义模块目录，相对算法包根目录 |

`paper` 基线另行使用 `upstream.yaml` 固定上游仓库地址和完整 Git commit；普通训练人员不能通过前端填写或修改该信息。

## 3. 改进包清单：`algorithm.yaml`

改进位于 `{baseline-id}/improvements/{improvement-id}/algorithm.yaml`：

```yaml
schema_version: 1
id: small-object/mmdet-faster-rcnn-r50-v1/feature-fusion-v1
kind: improvement
name: 小目标特征融合 v1
parent: small-object/mmdet-faster-rcnn-r50-v1
direction: small-object
engine: mmdet
engine_version: 3.0.0
status: draft
data_format: coco
entry: mmdet/config.py
source_root: src
description: 在父基线基础上增加多尺度特征融合模块。
stack:
  enabled: true
  priority: 20
  requires: []
  conflicts: []
  override_paths:
    - model.neck
parameters: []
```

改进的 `parent`、`direction`、`engine`、`engine_version` 和 `data_format` 必须与父基线匹配。目录扫描阶段会返回校验错误，但不会执行配置或源码。

## 4. 改进包组合规则

训练页面会调用目录接口解析“基线 + 已勾选改进包”。解析顺序为 `stack.priority` 从小到大、同优先级按包 ID 排序。保存任务与启动训练前都会重复校验，不能仅依赖前端。

- `stack.enabled` 为 `true` 的包才可以勾选。
- `requires` 中的完整改进包 ID 必须同时被选择。
- `conflicts` 中的包不能同时被选择。
- 两个包声明相同 `override_paths` 时会被拒绝，避免覆盖顺序不明确。
- 基线和全部已选改进包的参数 `key` 必须唯一；最终参数列表才会渲染到前端。
- Runner 只接受最终参数列表中声明的 key，前端请求中额外拼接的训练参数会被拒绝。

## 5. 动态参数协议

仅在 `parameters` 中显式声明的字段才能显示给训练人员。不要从任意 Python 配置自动开放所有字段。

```yaml
parameters:
  - key: train.learning_rate
    label: 学习率
    description: 优化器初始学习率。
    type: number
    default: 0.0001
    min: 0.000001
    max: 0.1
    step: 0.00001
    config_path: optim_wrapper.optimizer.lr
    editable_by:
      - PlatformAdmin
      - BaselineMaintainer
      - ImprovementDeveloper
      - ExperimentOperator
```

允许的 `type`：`number`、`integer`、`boolean`、`select`、`string`、`json`。后续参数渲染器会根据该字段生成控件、校验范围并写入对应引擎配置。

## 6. 生命周期和目录安全

- 仅 `published` 算法包可供普通训练人员选择。
- 已有训练记录引用的包不得删除；应改为 `deprecated` 或 `archived`。
- `id`、`entry`、`source_root` 均为项目内相对路径，不能包含 `..`、盘符或绝对路径。
- 训练产生的配置、源码快照、日志、权重和论文源码下载缓存不属于本目录的版本控制内容。
