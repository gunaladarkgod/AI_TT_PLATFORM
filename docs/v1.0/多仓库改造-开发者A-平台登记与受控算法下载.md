# 多仓库改造任务书 A：平台登记与受控算法下载

**负责人：** 开发者 A（平台核心、后端和前端）

**建议分支：** `feature/algorithm-registry-and-download`

**PR 目标：** `develop`

## 1. 本任务要解决的问题

平台采用以下三类仓库，但平台仓库 A 不保存基线 B 或算法 C 的实际源码：

```text
A：AI_TT_PLATFORM（平台核心）
   ├─ 前后端、数据集、统一 Runner、日志和结果管理
   ├─ 研究方向公约、算法登记元信息
   └─ 本地已下载 B 基线和 C 算法的受控管理功能

B：AI_TT_BASELINE_<方向>_<基线>（基线仓库）
   └─ 可独立运行的基线源码、入口、依赖和实验说明

C：AI_TT_ALGO_<方向>_<方案>（独立算法仓库）
   └─ 从一个 B 复制/派生而来，包含完整代码及本方案修改
```

用户在平台中看到的是已登记的 B 和 C；平台管理员可选择下载到**本机平台工作目录**。B 只用于版本查看、清单校验和后续基线验证，C 才是后续正式训练的候选。平台远程仓库只提交登记信息、接口与页面，不提交任何 B/C 源码。普通训练用户只能使用管理员已接入且预检通过的 C。

本任务的目标是实现并验证 A 的“登记、展示、受控下载、状态说明”闭环；不要求迁移现有训练引擎，也不要求把全部旧算法变成 C。

## 前端开发者必须对接的接口（本次后端先行交付）

开发者 A 中负责前端的人员必须区分 **B 基线组件接口** 与 **C 独立算法组件接口**，不能复用旧的 `/api/workspace/components`。旧接口是同一平台仓库的 sparse checkout；B/C 则是克隆到 `local-components/` 的独立 Git 仓库，目录、权限、状态和删除策略都不同。

所有接口的外层均为现有 `AjaxResult`：成功时 `code=0`、业务数据在 `data`；失败时读取 `message`。浏览器**不得**提交 Git URL、ref、命令、绝对路径或本机目标目录。

| 前端用途 | 方法和地址 | 访问权限 | 请求 | 前端应如何使用 |
| --- | --- | --- | --- | --- |
| 列出登记 C 与本机状态 | `GET /api/algorithm-components?direction=small-object` | 所有已登录用户 | `direction` 可选 | 页面首屏加载；组件任务运行时每 2 秒轮询，任务结束后停止轮询。 |
| 读取训练选择/预检资料 | `GET /api/algorithm-components/training-profile?id=small-object/tdsc` | 所有已登录用户 | 登记 ID | 训练页选中 C 后调用。当前只显示状态和阻塞原因，不直接启动训练。 |
| 下载已登记 C | `POST /api/algorithm-components/install` | 仅平台管理员 | `{"id":"small-object/tdsc"}` | 管理员点击下载后轮询列表中的 `data.task`；其他角色不显示或禁用此按钮。 |
| 移除本机 C 副本 | `POST /api/algorithm-components/remove` | 仅平台管理员 | `{"id":"small-object/tdsc"}` | 必须二次确认；只删除本机副本，不删除远程 C/B 仓库。 |
| 列出登记 B 与本机状态 | `GET /api/baseline-components?direction=small-object` | 所有已登录用户 | `direction` 可选 | 基线管理页首屏加载；任务运行时每 2 秒轮询。 |
| 读取 B 验证资料 | `GET /api/baseline-components/validation-profile?id=small-object/yolo11-baseline` | 所有已登录用户 | 登记 ID | 基线页选中 B 后调用；只展示验证前提，不直接启动训练。 |
| 下载/移除 B | `POST /api/baseline-components/install` / `remove` | 仅平台管理员 | `{"id":"small-object/yolo11-baseline"}` | 下载位置固定为 `local-components/baselines/`；不影响任何 C。 |

`GET` 返回的 `data.components` 每项至少包含：

```json
{
  "id": "small-object/tdsc",
  "name": "小目标 TDSC 独立算法",
  "direction": "small-object",
  "registrationStatus": "draft",
  "engine": {"id": "ultralytics", "version": "unverified"},
  "dataFormat": "yolo",
  "localStatus": "not-downloaded",
  "trainingReady": false,
  "blockers": ["本机尚未下载该算法组件"],
  "sourceCommit": null,
  "derivedFrom": {"baseline": {"repository": "...", "ref": "...", "commit": "..."}},
  "installAllowed": true,
  "removeAllowed": false
}
```

状态展示规则：

1. `localStatus=not-downloaded`：显示“未下载”；只有平台管理员可见“下载”按钮。
2. `localStatus=invalid`：显示“下载内容不可用”，完整列出 `blockers`；不得在训练下拉框中显示为可选项。
3. `localStatus=downloaded` 但 `trainingReady=false`：代码和清单一致，但登记状态、环境或 Runner 接入仍未满足；显示原因，不得创建训练任务。
4. 只有 `trainingReady=true` 才进入“可创建训练任务”的 C 下拉框。当前试点阶段可能没有任何可训练 C，这是正确状态，不能用前端默认值绕过。
5. `data.task.status` 为 `running|succeeded|failed`；展示 `message` 和 `logs`，不得自己推测 Git 失败原因。页面卸载不取消后台下载，重新进入页面可从 GET 的 `task` 恢复状态。

前端 API 封装应集中写在 `fronternd/src/api/api.js`，建议函数命名为 `fetchAlgorithmComponents`、`fetchAlgorithmTrainingProfile`、`installAlgorithmComponent`、`removeAlgorithmComponent`。训练表单不得直接拼 Runner 命令：本阶段 Runner 尚未执行 C，后续由后端在清单验证完成后受控接入。

基线页面增加 `fetchBaselineComponents`、`fetchBaselineValidationProfile`、`installBaselineComponent`、`removeBaselineComponent`。B 的 `baselineValidationReady=true` 仅允许进入后续“基线验证”流程，不能加入 C 的“正式算法训练”下拉框；B 的下载、更新或移除也不得影响任何已下载 C。

**阶段边界：** 当前试点 C `small-object/tdsc` 已登记为 `draft`，而 C 仓库尚未提供 `algorithm.yaml`、`derived-from.yaml` 和经过验证的 Runner 入口。因此前端应如实呈现“未验证/不可训练”，不能将“已登记”翻译成“可训练”。

## 2. A 平台目标目录与本机组件路径

本节是 A 平台的目标结构说明。它的作用是让开发者明确：**A 负责平台能力和登记，B/C 的源码只在授权机器的本机缓存中出现。** 目录迁移必须单独立项；本任务不能因为新增目录说明而移动、删除或重命名旧训练目录。

```text
AI_TT_PLATFORM/
├─ backend/                    # 后端：认证、权限、数据集、任务、登记读取、受控下载和结果接口
├─ fronternd/                  # 前端：平台页面、训练表单、B/C 登记与本机组件管理页面
├─ runner/                     # 平台统一 Runner：任务调度、预检、日志和产物协议；不存放某个 B/C 源码
├─ engines/                    # 现有旧训练链路和兼容资源；当前仍被旧功能引用，禁止在本任务删除
├─ mmdet_run/                  # 历史 MMDet 兼容目录；新代码不得新增依赖，移除须在 engines 迁移任务后单独验证
├─ data/                       # 平台管理的数据集、中间实例数据集和预处理产物；不是 B/C 源码目录
├─ artifacts/                  # 现有任务日志、配置和训练/推理产物；后续按研究方案细分须另行迁移验证
├─ research/                   # 历史研究代码或兼容内容；不等同于新的 B/C 仓库结构
├─ research-registry/          # 可提交的研究方向、B/C 登记、公约和模板；只保存元信息，不保存源码
├─ local-components/           # 本机按需下载的 B/C Git 工作区；必须被 .gitignore 忽略
│  ├─ baselines/               # 下载的 B：仅验证、查看和派生 C，不作为正式算法训练候选
│  ├─ algorithms/              # 下载的 C：经登记、清单校验和预检后，才可能成为训练候选
│  ├─ .staging/                # 下载中的临时目录；成功或失败收尾后由服务清理
│  ├─ .platform-state/         # 预留的平台本机状态目录；当前前端不得依赖它一定存在
│  └─ .locks/                  # 预留的并发操作锁目录；当前前端不得依赖它一定存在
├─ docs/                       # 面向使用、维护、协作和架构的文档
├─ logs/                       # 平台自身历史日志；不替代 artifacts/ 中的任务产物
├─ launcher/                   # 本机启动辅助脚本/启动器（如当前仓库存在的启动入口）
├─ README.md                   # 项目入口、启动说明和文档索引
└─ .gitignore                  # 明确排除 local-components、数据、产物、日志、环境和凭据
```

其中 `backend/`、`fronternd/`、`runner/`、`research-registry/` 是 A 的长期核心；`engines/`、`mmdet_run/`、`research/` 是目前仍需兼容的存量目录，不能简单视为冗余。`data/`、`artifacts/`、`logs/` 和 `local-components/` 都是本机运行数据，是否提交应由 `.gitignore` 和现有数据管理规则控制。

### 2.1 `local-components/` 的固定语义

本机缓存必须按“组件类型 / 研究方向 / 稳定组件 ID”分层，不能由页面传递任意磁盘路径。目标形态如下：

```text
local-components/
├─ baselines/
│  └─ small-object/
│     └─ yolo11/                # 下载的 B，仅用于验证、查看和派生
└─ algorithms/
   └─ small-object/
      └─ tdsc/                  # 下载的 C，可作为平台训练候选
```

每个已下载目录都是相应独立仓库的 Git 工作区，保留自己的 `.git/`、清单、README、配置和脚本；A 不向其中写入平台源码，也不将其设为 Git 子模块。B 与 C 在运行时互不依赖：删除 B 不应破坏已下载的 C，C 也不能通过相对路径导入 B。

当前实现由登记 ID 推导目录，因此现有试点的实际映射是：

| 类型 | 当前登记 ID | 当前实际本机目录 | 用途 |
| --- | --- | --- | --- |
| B | `small-object/yolo11-baseline` | `local-components/baselines/small-object/yolo11-baseline/` | 基线验证、查看和派生来源 |
| C | `small-object/tdsc` | `local-components/algorithms/small-object/tdsc/` | 独立算法候选 |

上面的 `yolo11/` 是建议的新基线命名形态；在不另行迁移登记 ID 的前提下，不得手工把现有 `yolo11-baseline/` 改名为 `yolo11/`，否则服务端状态识别会失效。若后续决定统一为 `yolo11`，必须同时迁移登记 ID、接口调用、测试、文档和已有本机组件，并单独验证。

### 2.2 A、B、C 的保存位置与责任边界

| 内容 | 远程保存位置 | A 本机保存位置 | 谁负责维护 | 不能做什么 |
| --- | --- | --- | --- | --- |
| 平台核心能力 | A 仓库 | A 根目录 | 平台维护者 | 不复制 B/C 完整源码到 A Git |
| 研究方向公约、登记、模板 | A 的 `research-registry/` | A 根目录 | 平台/方向负责人 | 不写入权重、数据、密钥或实验源码 |
| B 基线完整代码 | B 独立仓库 | `local-components/baselines/...`（按需） | 基线负责人 | 不作为 A 子模块或 C 的运行时依赖 |
| C 算法完整代码 | C 独立仓库 | `local-components/algorithms/...`（按需） | 方案负责人 | 不只交付 diff/改进包或依赖 B 本机目录 |
| 训练任务结果 | 当前 A 的 `artifacts/` 规则 | A 本机 | 平台任务系统 | 不能把结果反向提交进 B/C/A 源码仓库 |

## 3. 总体边界

### 3.1 必须遵守的架构边界

1. A 只保存 B/C 的元信息，不保存 B/C 的训练源码、权重、数据集、虚拟环境或 Git 凭据。
2. B 不是 A 的子模块，也不由 A 在运行时依赖。管理员可以将 B 按需克隆到 `local-components/baselines/`，但该本机缓存不能进入 A 的 Git 历史；B 只用于创建/维护 C 的起点及独立基线验证。
3. C 是完整、可独立维护的算法仓库，不是“改进包”或需要与 B 动态拼装的插件。
4. A 下载 C 后，C 只保存在本机的受控目录；该目录必须被 `.gitignore` 忽略，禁止把下载内容再次提交到 A。
5. 平台不得接受浏览器提交的任意仓库 URL、Git 参数、本机绝对路径或执行命令。
6. GitHub 仓库的读取权限由 GitHub 控制；平台四级角色只控制平台页面和接口权限，两者不能互相替代。

### 3.2 本任务明确不做

- 不创建或修改 B、C 仓库中的算法源码；由开发者 B 负责。
- 不把当前 `engines/`、`runner/`、`research/` 批量移动到外部仓库。
- 不删除现有本地训练、数据集和结果查询功能。
- 不允许任意用户在页面粘贴 Git URL 后执行克隆。
- 不启动、停止或重启后端、Runner。

## 4. A 侧登记协议

每一个可被平台下载的 C 必须在 `research-registry/algorithms/` 下有一份登记文件。登记是平台侧索引，不等于用户已获得该算法源码。

建议在现有协议中补充/冻结下列字段：

```yaml
schema_version: 1
id: small-object/yolo11-augmentation-v1
name: YOLO11 小目标增强方案 v1
kind: standalone-algorithm
direction: small-object
status: review

engine:
  id: ultralytics
  version: 8.4.115
data_format: yolo

source:
  type: external-git
  repository: https://github.com/<organization>/AI_TT_ALGO_SMALL_OBJECT_YOLO11_AUGMENTATION.git
  default_ref: develop
  stable_ref: v0.1.0
  manifest: algorithm.yaml
  derived_manifest: derived-from.yaml

derived_from:
  baseline:
    repository: https://github.com/<organization>/AI_TT_BASELINE_SMALL_OBJECT_YOLO11.git
    ref: v1.0.0
    commit: <40 位提交 ID>

platform:
  min_version: null
  supported_runner_protocol: 1

description: 说明方案解决的问题、当前验证状态和已知限制。
```

规则如下：

| 字段 | 要求 |
| --- | --- |
| `id` | 全局唯一，格式为 `方向/方案简称`，仅使用小写字母、数字和连字符。 |
| `kind` | 新架构固定使用 `standalone-algorithm`；历史 `baseline`、`improvement` 记录仅作迁移兼容，不得误标为新 C。 |
| `source.repository` | 仅能由平台仓库受控登记文件提供；不能来自浏览器请求。不得带 Token、密码或私钥。 |
| `source.stable_ref` | 优先 Release Tag 或固定提交；为空表示开发版，只能在页面明确标注“未固定版本”。 |
| `source.derived_manifest` | 固定为 C 根目录相对路径（通常是 `derived-from.yaml`）；下载后与平台登记的 `derived_from.baseline` 逐字段比对。 |
| `derived_from.baseline` | C 必填，记录创建它的 B 仓库、基线版本和精确提交，便于追溯。 |
| `status` | `draft`、`review`、`published`、`deprecated`、`archived`。只有下载、预检和真实训练验证均记录完成后，才可设为 `published`。 |
| 路径 | `manifest` 等相对路径不得包含盘符、绝对路径或 `..`。 |

### 4.1 本机算法目录与状态文件

统一使用平台根目录下、但不提交 Git 的目录。算法源码、平台管理状态和临时下载目录必须分开，避免把平台状态写进 C 的 Git 工作区：

```text
local-components/
├─ algorithms/                              # 已成功下载、可供选择的 C
│  └─ small-object/
│     └─ yolo11-augmentation-v1/
│        ├─ .git/                           # C 自己的 Git 工作区
│        ├─ algorithm.yaml
│        ├─ derived-from.yaml
│        ├─ configs/
│        ├─ src/
│        └─ scripts/
├─ .platform-state/                         # A 写入的本机状态，不进入 C 仓库
│  └─ small-object--yolo11-augmentation-v1.json
├─ .staging/                                # 下载/更新时的临时目录；成功后必须清理
└─ .locks/                                  # 可选：防止同一 C 被并发下载、更新或删除
```

目录由服务器根据登记 `id` 推导；不得让客户端传递最终路径。`.gitignore` 必须忽略 `/local-components/`，并在 README 说明该目录是可重新下载的本机组件缓存。

`.platform-state/<安全文件名>.json` 只保存平台管理所需的非敏感事实，例如登记 ID、目标 ref、实际提交 ID、清单摘要、下载时间、最近预检时间、状态和最后失败原因；不能保存 GitHub Token、SSH 私钥、密码、数据集绝对路径、训练参数或用户个人路径。C 本身的 Git 状态仍由 C 仓库管理，平台在发现未提交改动时必须拒绝更新或移除，而不是覆盖。

### 4.2 `research-registry/` 的内容与职责

`research-registry/` 是 A 中**唯一提交到平台远程仓库**的研究方案索引。它保存规则和元信息，不保存 B/C 源码。推荐目标结构如下：

```text
research-registry/
├─ README.md                               # 协议说明、状态含义、迁移边界
├─ registry.yaml                           # schema 版本、允许的引擎/格式/状态、目录入口
├─ directions/                             # 每个研究方向的公共定义
│  ├─ small-object.yaml
│  └─ knowledge-distillation.yaml
├─ conventions/                            # 所有 C 共用的只读公约
│  ├─ dataset-format.md                    # COCO/YOLO/custom 格式与必需文件
│  ├─ evaluation-metrics.md                # 指标名称、比较条件、结果记录规则
│  ├─ runner-protocol.md                   # C 与统一 Runner 的入口、参数、输出约定
│  ├─ repository-lifecycle.md              # B/C 派生、Tag、升级、弃用规则
│  └─ security-and-permissions.md          # GitHub 权限与平台角色边界
├─ algorithms/                             # 一项 C 对应一份平台登记
│  └─ small-object--yolo11-augmentation-v1.yaml
└─ templates/                              # 新建登记或 C 清单时复制的模板
   ├─ standalone-algorithm-registration.yaml
   ├─ algorithm.yaml
   └─ derived-from.yaml
```

各类文件的内容边界：

| 位置 | 保存什么 | 不保存什么 |
| --- | --- | --- |
| `registry.yaml` | Schema 版本、允许枚举、加载入口、兼容策略 | 具体算法源码或本机安装状态 |
| `directions/*.yaml` | 方向名称、简介、允许数据格式、公共评测约束、维护状态 | 某个 C 的私有代码、实验结果 |
| `conventions/` | 各方向共同遵守的数据、Runner、仓库生命周期和权限规则 | 可执行训练脚本、密钥 |
| `algorithms/*.yaml` | C 的 ID、显示名、仓库地址、固定版本、引擎、格式、B 来源、登记状态 | C 的源码、权重、数据集、Token |
| `templates/` | 新建 C 或登记时使用的字段模板 | 已验证算法的真实生产配置 |

例如 `directions/small-object.yaml` 应说明“小目标检测”的方向 ID、支持的输入格式、最低记录指标及适用公约；`algorithms/small-object--yolo11-augmentation-v1.yaml` 才描述某个具体 C 的远程仓库和它来自哪个 B。这样 A 的 GitHub 仓库始终只有描述信息，实际 C 代码只存在于 C 私有仓库和授权用户的 `local-components/algorithms/` 中。

### 4.3 从本机已下载组件选择算法

模型训练页面的算法选择应遵循固定流程：

```text
平台登记 C
   → 管理员下载到 local-components/algorithms/
   → 平台校验 C 的 algorithm.yaml 与 derived-from.yaml
   → C 出现在“已下载算法”列表
   → 实验操作员选择 C
   → 按本次 Python、数据格式、Runner 状态执行预检
   → 预检通过后才允许创建/启动训练任务
```

具体规则：

1. 训练页面的下拉列表只读取“登记存在且本机已下载、清单匹配”的 C；不能直接罗列远程仓库，也不能让用户输入 URL。
2. 已下载但环境未检查、Runner 未启动、Python 缺依赖或数据格式不匹配的 C 仍可显示，但必须带“不可训练”的具体原因；不能伪装成可启动。
3. C 被删除、损坏、登记撤销、工作区有未处理冲突或清单不一致时，立即从可选择列表移除，并保留管理员可见的诊断信息。
4. 选择 C 后，任务保存记录登记 ID、C 的实际提交 ID、B 来源提交、清单摘要和最终参数；这些信息写入运行规格，不写回平台注册文件。
5. 后续若要升级 C，只允许管理员按登记的固定 ref/新审核版本更新；旧任务继续引用当时实际使用的提交，不能被静默改写。

## 5. 阶段性工作与目标

每个阶段结束后建立可审查的本地提交。未达到“完成标志”时，不得写为已完成或已接入。

### 阶段 A0：协议调整与迁移边界

**要做什么：**

1. 阅读 `README.md`、技术说明、GitHub 协作流程、算法集成规范和现有 `research-registry/`。
2. 将协议文档和模板从“可叠加改进包”调整为“独立完整 C 算法仓库”。
3. 写明旧 `research/`、旧 `baseline/improvement` 登记的迁移状态：它们仍可用，但不代表已经是新的 B/C 结构。
4. 与开发者 B 确认 `algorithm.yaml`、`derived-from.yaml` 和 A 侧登记之间的字段对应关系。
5. 对现有 `engines/` 建立依赖清单，明确它仍是旧 MMDet/Ultralytics 训练链路的一部分，而不是本阶段可删除目录。

**完成标志：** 开发者 B 能根据模板新建 B 和 C；A 能仅靠登记文件确定一个 C 的仓库、固定版本、数据格式、引擎和基线来源。

**未完成时不能声称：** 协议更新不等于平台能下载算法，更不等于算法可训练。

### 阶段 A1：后端只读登记与一致性校验

**要做什么：**

1. 实现/完善固定读取 `research-registry/` 的服务，不能改为数据库模板记录。
2. 校验 ID 唯一性、方向存在性、状态、引擎、数据格式、Git URL 安全性、相对路径以及 `derived_from` 必填性。
3. 为历史记录保留兼容读取；新 C 记录与历史记录要在返回结构中明确区分。
4. 提供只读接口，返回登记信息、字段错误和具体不可用原因。

**完成标志：** 无效 YAML、重复 ID、缺少 `derived_from`、不安全路径、未知方向或版本字段错误，都能被服务端拒绝并说明原因；测试覆盖正常和失败场景。

**未完成时不能声称：** 能将 YAML 原文返回前端，不表示登记已被校验。

### 阶段 A2：本机下载状态与预检模型

**要做什么：**

建立统一状态模型。建议将“登记状态”和“本机状态”分开，避免把已登记误报为可训练：

| 类别 | 示例状态 | 含义 |
| --- | --- | --- |
| 登记状态 | `draft/review/published` | 方案资料的审核成熟度。 |
| 本机组件状态 | `not-downloaded/downloaded/invalid/outdated` | 当前电脑是否已有对应 C，以及清单是否匹配。 |
| 运行预检状态 | `not-checked/blocked/ready` | Runner、Python、数据格式、入口等是否满足本次任务。 |

读取本机 C 时，至少核对登记与 `algorithm.yaml` 的 `id`、引擎、版本、数据格式，以及 `derived-from.yaml` 的来源信息；不一致时必须显示哪一个字段不一致。

**完成标志：** 页面/API 能区分“平台知道该 C”“本机没有下载”“下载内容不正确”“代码存在但环境不满足”“可以进入训练预检”。

**未完成时不能声称：** 本机目录存在或 Git 克隆成功，都不等于可训练。

### 阶段 A3：受控下载服务

**要做什么：**

1. 仅允许 `PlatformAdmin` 发起下载、更新或移除本机 C；其他角色只读状态。
2. 请求只提交登记 `id` 和允许的动作；服务端从登记文件读取仓库、目标 ref 和本机目标目录。
3. 下载前检查：登记状态、仓库地址格式、目标目录为空/已存在、Git 可用性、当前机器访问私有仓库的权限。
4. 对已存在目录明确处理策略：首次下载、按固定 ref 更新、工作区有改动时拒绝覆盖并返回原因；不得静默丢弃本地修改。
5. 下载完成后立即读取并校验 `algorithm.yaml` 与 `derived-from.yaml`；校验失败时保留失败原因，且不标记为可用。
6. 不保存 GitHub Token、SSH 私钥、用户名密码；Git 凭据由当前操作系统/用户已配置的 Git 凭据管理器或 SSH Agent 提供。

**完成标志：** 对一个预先登记、当前用户有读取权限的测试 C，可在本机受控目录完成下载；无权限、仓库不存在、网络失败、清单缺失、字段不一致、本地目录有改动时均返回明确原因。

**未完成时不能声称：** 禁止把“允许页面填写 URL 并执行 clone”作为实现完成；那会造成任意代码下载和后续执行风险。

### 阶段 A4：研究基线公约与算法选择页面

**要做什么：**

1. 新增或完善“研究基线公约/算法登记”页面，展示研究方向、规则、C 的基线来源、版本、引擎、格式、登记状态和本机状态。
2. 为管理员提供下载、更新、移除按钮；普通用户只能看到已下载算法与不可用原因。
3. 模型训练页选择 C 时，只显示已下载且清单一致的算法；尚未通过运行预检的方案要提示原因而非隐藏事实。
4. 页面不得显示凭据、绝对用户目录或私有 Git 命令。

**完成标志：** 用户能理解“B 是来源、C 是当前完整方案、A 只管理登记和本机副本”；管理员与实验操作员看到的操作权限不同。

**未完成时不能声称：** 页面能显示仓库链接，不代表当前机器已经获得源码或可以训练。

### 阶段 A5：文档、测试与旧功能回归

**要做什么：**

1. 更新 README、技术说明、算法集成规范和登记协议。
2. 说明“平台远程仓库不会包含 C 源码，但每个 C 自己的远程仓库包含完整派生代码；本机只下载所需 C”。
3. 为协议、权限、状态转换、受控下载和错误提示补充测试。
4. 进行前端构建、后端编译和现有训练/数据集/结果页面回归检查。

**完成标志：** 文档与实现一致；不存在仍宣称“改进包可任意动态叠加”的过期说明。

### 阶段 A6：`engines/` 迁移评估（独立后续任务，不与 A0-A5 混做）

**当前结论：** 当前 `engines/` 不能删除。现有 MMDet 模板、配置生成、Runner 启动脚本、后端默认路径、前端默认表单、组件下载服务和测试都仍直接引用 `engines/mmdet_run` 或 `engines/yolo_run`。删除会导致现有训练任务不可用。

**未来要做什么：** 只有在所有仍承诺支持的旧训练链路都已迁移到 B/C，且统一 Runner 已只根据 C 清单解析入口时，才能单独创建迁移分支处理 `engines/`。该迁移必须包含：

1. 将仍需支持的 MMDet 模板、配置生成逻辑和运行声明迁移到对应 B/C；
2. 清除 Runner、后端、前端、启动器、测试与文档中的 `engines/...` 默认路径和回退路径；
3. 为历史训练任务提供只读兼容/迁移说明，或正式宣布不再支持；
4. 使用至少一个 MMDet C 和一个 Ultralytics C 完成端到端验证；
5. 全局搜索确认没有生产路径引用后，再删除 `engines/` 并做完整回归测试。

**完成标志：** 没有任何启动、创建任务、训练、查看配置、查看历史结果或测试需要 `engines/`；不是“新 C 已能运行”就可以删除。

## 6. 验收清单

- [ ] A 中没有 B/C 算法源码；`local-components/` 已被忽略且不会进入提交。
- [ ] A 可从 `research-registry/` 读取并校验 C 的登记信息，不依赖数据库模板表。
- [ ] C 的登记记录包含仓库、固定版本/开发版本标记、引擎、格式和 `derived_from`。
- [ ] 后端不接受客户端任意 Git URL、命令或本机路径。
- [ ] 仅 `PlatformAdmin` 可触发下载、更新、移除；其他固定角色权限符合现有定义。
- [ ] 能清晰返回“未登记、未下载、无仓库权限、网络失败、清单不一致、本地改动未提交、环境不满足”等原因。
- [ ] 已有 `research/` 和现有训练功能不因本任务失效。
- [ ] 训练页只允许选择已下载且清单匹配的 C，并在预检失败时显示明确原因。
- [ ] `engines/` 在未完成独立迁移任务前保持不动；没有依据不得删除。
- [ ] 前端构建、后端编译及新增测试通过；未真实训练的项目必须标注为未验证。

## 7. 交接给开发者 B 的内容

开发者 A 至少交付：

1. 最终 `algorithm.yaml` 与 `derived-from.yaml` 样例；
2. C 登记文件的固定字段和状态枚举；
3. 平台实际使用的本机组件目录约定；
4. 下载后会校验的字段列表；
5. 任何尚未实现的 Runner 接入限制。

开发者 B 至少反馈：C 的稳定 Tag/提交、`algorithm.yaml`、`derived-from.yaml`、最小训练入口、依赖版本及真实/未真实验证的边界。

## 8. 风险提示

- GitHub 私有仓库权限是第一道源码权限；平台下载接口不能绕过 GitHub 授权。
- 每个 C 独立保存一份基线派生代码，会增加 C 仓库体积；这是用存储换取隔离、可复现和避免跨改进冲突的主动选择。
- 当前 `component-download` 的 sparse checkout 是同一仓库按目录获取文件的旧思路，不等同于从独立 C 仓库受控下载；实现时必须明确迁移或兼容策略，不能混为一谈。
