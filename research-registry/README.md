# 研究基线登记中心

本目录是 A 层维护的研究方向、基线和算法元信息登记，不保存独立 B/C 仓库的源码。A 同时提供公共平台能力和 YOLO、MMDetection 等引擎的接入、版本约束、环境检查与统一调度；第三方框架源码按需安装，不复制进平台登记目录。

## A/B/C 关系

| 层级 | 含义 | 仓库职责 |
| --- | --- | --- |
| A | 平台与公共核心组件 | 管理数据集、任务、评测、引擎接入、Runner、登记及受控下载；不保存 B/C 训练源码。 |
| B1 | 小目标检测方向 | 方向下登记可运行基线；用户选择具体基线后可据此派生 C。 |
| B2 | 知识蒸馏方向 | 方向下登记可运行基线；知识蒸馏能力必须由基线清单和实现证明，不能仅凭方向名称推断。 |
| B3 | 时空方向 | 方向下登记可运行基线；具体数据格式和引擎以基线清单为准。 |
| C | 独立算法仓库 | 用户从一个固定 B 基线派生完整代码，在自己的 C 中修改；运行不依赖 B 的本机目录。 |

方向 ID（如 `small-object`）是机器标识，`code`（如 `B1`）是讨论时使用的层级编号。一个方向可以有多个基线，一个基线也可以派生多个 C。方向登记本身不代表该方向已有可运行基线。

项目内旧 `research/` 目录仍是现有训练页的兼容机制；登记状态为 `draft` 的方案不能表述为已接入训练。旧训练链路及 `engines/` 在新 B/C 流程验证完成前继续保留。

## 固定字段

方向文件位于 `directions/`，至少包含：

- `schema_version: 1`
- `id`：全局唯一的小写方向 ID，例如 `small-object`
- `code`：人类可读的层级编号，例如 `B1`、`B2`、`B3`
- `name`、`description`、`status`：状态为 `active`、`deprecated` 或 `archived`
- `data_formats`、`metrics`、`engine_compatibility`

算法登记文件位于 `algorithms/`，至少包含：

- `schema_version: 1`、`id`、`name`、`direction`、`description`
- `kind`：`baseline`、`improvement` 或 `standalone-algorithm`
- `status`：`draft`、`review` 或 `published`
- `repository.url`、`repository.default_ref`、`repository.stable_ref`
- `platform.min_version`
- `engine.id`、`engine.version`：引擎 ID 为 `mmdet`、`ultralytics`、`custom` 或 `paper`
- `data_format`：`coco`、`yolo` 或 `custom`
- `algorithm_manifest`：登记对应的算法清单相对路径；独立 C 算法还应提供 `derived_manifest`

`id` 使用 `方向/方案简称`，仅允许小写字母、数字和连字符。仓库地址仅作展示和后续受控接入的元信息，当前接口不会把它交给 Git 执行。

## 接口与状态

登录后访问 `GET /api/research-registry`。成功时返回 `registry`、`directions`、`algorithms`、`conventions`；登记目录缺失、YAML 无效、字段缺失、重复 ID、未知引擎、非法路径或公约文件读取失败时，响应 `data.errors` 会列出原因。研究基线页面还提供管理员专用的 B/C 仓库按需下载接口：

- `GET /api/baseline-components`：读取登记的 B 基线和本机状态。
- `GET /api/algorithm-components`：读取登记的 C 算法和本机状态。
- `POST /api/baseline-components/install`、`POST /api/algorithm-components/install`：按登记 ID 克隆对应 GitHub 仓库。
- `POST /api/baseline-components/remove`、`POST /api/algorithm-components/remove`：移除对应的本机副本，不影响远程仓库。

下载写入被 `.gitignore` 忽略的 `local-components/`，不会把外部 B/C 源码提交进 A 仓库。仓库已下载和可训练是两个状态：若仓库缺少 `algorithm.yaml` 或 C 的 `derived-from.yaml`，页面会保留本机代码并显示待补清单；只有登记状态为 `published` 且清单齐全时才标记为可训练。下载和移除操作要求平台管理员、基线维护者或改进开发者权限，普通实验用户只能查看状态。

页面显示的登记状态含义如下：

| 状态 | 含义 |
| --- | --- |
| `draft` | 已登记元信息，仍在开发或验证，不能视为可训练 |
| `review` | 等待评审和兼容性确认 |
| `published` | 已通过登记流程，可作为公开研究方案说明；训练可用性仍以引擎和源码验证为准 |
