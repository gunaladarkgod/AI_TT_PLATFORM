# 多仓库改造任务书 B：基线派生与独立算法仓库试点

**负责人：** 开发者 B（算法、训练入口与可复现性）

**建议分支：** `feature/baseline-derived-algorithm-pilot`

**PR 目标：** `develop`

## 1. 本任务要解决的问题

不再将“改进”做成可在运行时任意叠加的改进包。原因是不同改进可能修改同一模块、训练入口或依赖，平台无法可靠预测组合冲突。

改为两类独立 Git 仓库：

```text
B：基线仓库
   AI_TT_BASELINE_SMALL_OBJECT_YOLO11
   └─ 一套完整、稳定、可独立运行的 YOLO11 小目标检测起点

C：算法仓库
   AI_TT_ALGO_SMALL_OBJECT_YOLO11_AUGMENTATION
   └─ 从 B 的固定版本复制/派生，保留完整代码，再加入本方案修改
```

C 是一个完整算法实现，不是补丁、插件或与 B 动态拼接的目录。C 的任何修改只能影响它自己的仓库；不会影响 B，也不会影响其他 C。A 平台只登记和按需下载 C，不保存 C 的源码。

本任务以一个真实、最小的试点验证此模式：

```text
研究方向：小目标检测
基线 B：YOLO11 + 官方 Ultralytics
算法 C：YOLO11 小目标增强方案 v1（具体增强实现由负责人确定并如实记录）
输入格式：YOLO data.yaml
```

## 2. 概念与目录边界

### 2.0 A 平台与本机组件目录：B 必须遵守的路径契约

B 负责 B/C 独立仓库的代码和清单，但必须理解 A 平台的本机目录边界，避免写出依赖平台私有路径的训练代码。A 的目标结构如下：

```text
AI_TT_PLATFORM/
├─ backend/                    # 平台后端、认证、登记、受控下载和任务接口
├─ fronternd/                  # 平台前端；不复制 B/C 的训练源码
├─ runner/                     # 统一 Runner；未来按清单受控调度，不是 B/C 代码目录
├─ engines/                    # 仍在使用的旧训练兼容链路；本任务禁止删除或依赖其私有路径
├─ mmdet_run/                  # 历史 MMDet 兼容目录；B/C 不得新增对它的路径依赖
├─ data/                       # 平台管理的数据集及预处理产物
├─ artifacts/                  # 平台任务的日志、配置和结果产物
├─ research/                   # 旧研究内容/兼容目录，不是新 B/C 的来源目录
├─ research-registry/          # 已提交的研究方向公约、B/C 登记和模板，不保存 B/C 源码
├─ local-components/           # 被 .gitignore 忽略的本机 B/C 下载缓存
│  ├─ baselines/               # B 的本机工作区
│  └─ algorithms/              # C 的本机工作区
├─ docs/                       # 使用、维护、协作文档
└─ logs/                       # 平台自身日志
```

本任务相关的本机目录约定为：

```text
local-components/
├─ baselines/
│  └─ small-object/
│     └─ yolo11/                # 下载的 B，仅用于验证、查看和派生
└─ algorithms/
   └─ small-object/
      └─ tdsc/                  # 下载的 C，可作为平台训练候选
```

服务端实际按登记 ID 推导目录，当前试点的真实映射如下：

| 类型 | 当前登记 ID | 当前实际目录 | 是否可成为平台训练候选 |
| --- | --- | --- | --- |
| B：YOLO11 基线 | `small-object/yolo11-baseline` | `local-components/baselines/small-object/yolo11-baseline/` | 否；仅验证、查看和派生 |
| C：TDSC | `small-object/tdsc` | `local-components/algorithms/small-object/tdsc/` | 只有清单、环境和 Runner 预检均通过后才可以 |

`yolo11/` 是后续建议的规范化目录名称；现有试点仍使用 `yolo11-baseline/`，不能手工改名。要统一名称时，必须由 A 同时迁移登记 ID、接口、测试和已有本机目录。B/C 的训练入口必须只接收调用方明确提供的数据集、输出目录和参数；不得假设自己位于某个绝对路径，也不得访问相邻的 B 或 C 目录。

`local-components/` 的每个目录保留它自身的 `.git/`，但这些工作区永远不提交回 A。A 下载 B/C 时会使用临时 `.staging/` 目录；B 无需也不得依赖该临时目录。B 与 C 不建立 Git 子模块关系，C 不通过软链接、相对导入或共享工作目录使用 B。

### 2.1 B 基线仓库应包含什么

```text
AI_TT_BASELINE_SMALL_OBJECT_YOLO11/
├─ algorithm.yaml                 # 基线本身的运行声明
├─ README.md                       # 环境、数据、训练、评测、输出说明
├─ requirements/
│  └─ requirements.txt             # 固定 ultralytics 等依赖版本
├─ configs/
│  └─ default.yaml                 # 默认训练参数
├─ scripts/
│  ├─ train.py                     # 训练入口
│  ├─ validate.py                  # 可选：评测入口
│  └─ smoke_test.py                # 最小环境检查
├─ src/                            # 该基线确实需要的辅助代码
├─ tests/
└─ examples/
```

B 必须能脱离 A 平台运行：给定符合要求的 Python 环境、YOLO `data.yaml` 和输出目录，能执行训练入口。B 不包含 A 的前后端、数据库、Runner，也不把 A 作为子模块。

B 在 A 中被下载后，主要用于三类受控动作：查看版本与文档、运行独立基线验证、从固定提交派生一个新的 C。B 不是 C 训练时的依赖；因此 B 被删除、升级或存在本地修改时，均不得改变任何已派生 C 的运行结果。

“使用官方 Ultralytics”通常意味着在 `requirements.txt` 固定版本（例如 `ultralytics==8.4.115`），而不是把整个第三方 Ultralytics 源码复制进仓库。只有确实修改了引擎源码，才需要将修改后的引擎 Fork/源码纳入 B，并在 README 中写明来源和差异。

### 2.2 C 算法仓库应包含什么

```text
AI_TT_ALGO_SMALL_OBJECT_YOLO11_AUGMENTATION/
├─ algorithm.yaml                  # 当前 C 的训练声明
├─ derived-from.yaml                # 精确记录来自哪个 B、哪个版本/提交
├─ README.md                        # 当前方案改了什么、怎样复现、限制是什么
├─ requirements/                    # C 的完整依赖，含 B 的依赖与新增依赖
├─ configs/                         # C 的默认配置
├─ scripts/                         # C 的真实训练/评测入口
├─ src/                             # C 的完整运行所需源码（含从 B 继承内容）
├─ tests/
└─ examples/
```

C 的远程仓库会保存一份完整的派生代码，因此确实会增加远程仓库总体体积；但 A 的远程仓库不会增加这些源码。开发人员本机也只需克隆正在开发或运行的 C，不需要把所有 C 下载到 A。

在 A 的 `local-components/algorithms/<direction>/<algorithm-id>/` 中，C 必须保持上述完整结构，且 `algorithm.yaml` 与 `derived-from.yaml` 位于仓库根目录。未提供这两个清单的 C 可以作为个人开发仓库存在，但不能登记为“可供平台下载和训练”的 C。

### 2.3 禁止的做法

- 不创建只包含几行 diff 或“改进包”的 C，再要求平台与 B 拼接运行。
- 不在 C 中引用开发者私有绝对路径、A 的本地 `research/` 目录或其他 C 的工作区。
- 不把数据集、权重、训练日志、虚拟环境、`node_modules` 或密钥提交到 B/C。
- 不把 B/C 作为 Git 子模块提交到 A；A 只会在本机受控目录下载已登记的 C。
- 不自行启动第二套 Runner。平台调度时仍由 A 的统一 Runner 启动任务。

## 3. 从 B 创建 C 的标准过程

以下流程必须写入 C 的 README，并在 `derived-from.yaml` 中留下可机器读取的事实记录。

### 3.1 固定 B 的起点

1. 在 B 完成最小训练验证后，创建 Release Tag，例如 `v1.0.0`。
2. 记录 B 的远程地址和精确 40 位提交 ID。
3. 禁止从不明确的本地改动、浮动分支或未提交工作区复制出 C。

### 3.2 创建独立 C 仓库

推荐流程：从 B 的固定 Tag 克隆到新目录，移除/替换远程地址，创建新的私有 C 仓库并推送。保留历史或使用 GitHub Template 均可，但必须保留 `derived-from.yaml`；不要只依赖 GitHub 页面上的 Fork 关系。

```text
AI_TT_BASELINE_SMALL_OBJECT_YOLO11@v1.0.0
             │
             └──复制/派生──> AI_TT_ALGO_SMALL_OBJECT_YOLO11_AUGMENTATION
                                  ├─ 继承完整可运行代码
                                  ├─ 写入 derived-from.yaml
                                  └─ 加入本方案修改和测试
```

### 3.3 记录来源

`derived-from.yaml` 最小格式：

```yaml
schema_version: 1
baseline:
  repository: https://github.com/<organization>/AI_TT_BASELINE_SMALL_OBJECT_YOLO11.git
  ref: v1.0.0
  commit: <40 位提交 ID>
created_at: 2026-11-08
change_summary: >
  在固定 YOLO11 小目标基线基础上加入的增强策略；
  此 C 不依赖其他算法仓库运行。
```

`algorithm.yaml` 最小格式：

```yaml
schema_version: 1
id: small-object/yolo11-augmentation-v1
name: YOLO11 小目标增强方案 v1
kind: standalone-algorithm
engine:
  id: ultralytics
  version: 8.4.115
data_format: yolo
entry:
  train: scripts/train.py
  validate: scripts/validate.py
default_config: configs/default.yaml
parameters: []
outputs:
  work_dir_argument: --work-dir
description: 说明本方案与 B 的真实差异和当前验证状态。
```

开发者 A 的平台登记与 C 中的 `algorithm.yaml` 必须至少保持以下字段一致：`id`、引擎 ID、引擎版本、数据格式、仓库地址、稳定版本信息和 B 来源。

## 4. 阶段性工作与预期目标

每一阶段应至少有一个可审查提交。没有满足完成标志时，必须写明阻塞原因，不能用“目录已创建”代替可复现验证。

### 阶段 B0：冻结基线范围和契约

**要做什么：**

1. 阅读 `README.md`、技术说明、GitHub 协作流程、算法集成规范，以及开发者 A 的任务书和 `research-registry/` 协议。
2. 确定试点 B 的唯一职责：官方 Ultralytics YOLO11 小目标检测基线。
3. 确定 Python 版本、`ultralytics` 固定版本、YOLO 输入要求、默认模型、默认训练参数、输出规则和最小评测指标。
4. 与开发者 A 确定 C 的 ID、登记字段和本机组件目录约定。

**完成标志：** 在没有 A 平台源码的情况下，也能说明 B 如何安装依赖、接受什么数据、从哪个脚本开始训练、产物写到哪里。

**未完成时不能声称：** 只选择了“YOLO11”或写了空 `requirements.txt`，不表示基线定义完成。

### 阶段 B1：建立并验证 B 基线仓库

**要做什么：**

1. 创建 B 私有仓库和目录骨架。
2. 完成 `algorithm.yaml`、默认配置、训练入口、依赖文件、README 和最小 smoke test。
3. 确保训练入口只接受相对配置/参数以及调用者提供的 Python、YOLO `data.yaml`、输出目录；不得把机器绝对路径写死。
4. 在干净或可说明的环境中运行依赖检查；条件允许时完成一次最小真实训练。

**完成标志：** B 自身能被独立克隆，并根据 README 完成依赖检查；若具有可用的小型 YOLO 数据集，至少能启动一次训练并产生可定位日志和输出。若环境/数据阻塞，需保存明确的命令、输出和阻塞原因。

**未完成时不能声称：** 仅能导入 `ultralytics` 或有一个 `train.py` 文件，不表示 B 可以独立训练。

### 阶段 B2：从固定 B 派生 C

**要做什么：**

1. 将 B 固定在 Tag 或完整提交上。
2. 从该版本创建独立 C 私有仓库，写入 `derived-from.yaml`。
3. 在 C 中实现一个确定的、可审查的算法修改；例如数据增强策略、模型结构或训练流程变化。修改不得只停留在 README 或参数名称。
4. 更新 C 的配置、依赖、测试和 README，明确列出相较 B 的文件差异和预期影响。

**完成标志：** 删除或断开 B 的本地目录后，C 仍可自行安装、读取配置和运行训练入口；`derived-from.yaml` 可准确追溯到创建 C 的 B 版本。

**未完成时不能声称：** C 指向 B 的软链接、从 B 导入本地模块或依赖 B 工作区，都不算独立算法仓库。

### 阶段 B3：C 独立训练和可复现性验证

**要做什么：**

1. 使用 C 自己的依赖、默认配置和训练入口进行最小验证。
2. 固定并记录 Python、依赖锁定版本、输入 `data.yaml` 格式、随机种子、最终参数、输出目录和评测方式。
3. 将实验说明写入 C README；记录基线 B 的对比方法，但不要伪造未完成的精度结果。
4. 创建 C 的稳定 Tag（例如 `v0.1.0`），或明确标注仅为开发分支。

**完成标志：** 其他有仓库权限的成员从 C 的固定版本克隆后，不依赖 B 或 A 的源码，能够完成至少环境预检；若做过真实训练，应能按记录重现训练启动和日志位置。

**未完成时不能声称：** 有 Git Tag 不等于训练已验证；结果无法复现时必须保持 `draft/review` 状态。

### 阶段 B4：登记到 A 并验证平台调度边界

**要做什么：**

1. 向开发者 A 提供 C 的仓库地址、稳定 Tag/提交、`algorithm.yaml`、`derived-from.yaml` 和依赖说明。
2. 配合 A 完成登记文件；A 下载 C 后，检查平台登记与 C 清单字段一致性。
3. 只在 A 的统一 Runner 已支持的范围内接入：任务创建时指定训练 Python，Runner 负责预检、启动、停止、日志和产物定位。
4. 若现有 Runner 尚不能调用 C 的入口，只记录明确差距和接口需求，不要自行新增第二个 Runner 或破坏既有 MMDet/Ultralytics 流程。

**完成标志：** 平台可明确显示 C 是“未下载、已下载但不一致、已下载待环境检查或可进入训练预检”；任何不能运行的原因可定位到登记、源码、环境、数据、Runner 或入口之一。

**未完成时不能声称：** C 能在命令行独立运行，不等于已经成功被平台调度；平台能下载 C，也不等于真实训练已完成。

## 5. 测试与验收清单

### B 基线仓库

- [ ] 没有 A 平台子模块、平台源码、数据集、权重、日志、本机环境或密钥。
- [ ] 依赖版本、Python 要求、数据格式、入口、输出和评测说明完整。
- [ ] `algorithm.yaml` 与 README 一致，入口和默认配置真实存在。
- [ ] 至少完成一次依赖/smoke 检查；真实训练是否完成如实记录。

### C 独立算法仓库

- [ ] 包含完整可运行代码，不依赖 B 本地目录或运行时拼装。
- [ ] 包含正确的 `derived-from.yaml`，能追溯 B 仓库、Tag 和提交。
- [ ] 明确列出与 B 的实质改动、潜在限制和验证状态。
- [ ] C 的依赖、训练入口、配置和测试自洽。
- [ ] 固定版本（Tag/提交）已提供给开发者 A。

### 与 A 的交接

- [ ] 平台登记字段与 C 清单一致。
- [ ] 未向平台传递任意 Git 命令、Token、私钥或用户绝对路径。
- [ ] 未新建第二套 Runner，未修改无关的 A 核心功能。
- [ ] 清楚列出：已验证、未验证、阻塞原因、下一步所需条件。

## 6. GitHub 与权限要求

1. B 和每个 C 均为独立私有仓库；有读取权限的人才可克隆源码。
2. B 的维护权限应限于基线维护者/项目负责人；C 的写入权限应限于对应方案维护者和必要审核者。
3. B 与 C 的 `main` 均应保护：禁止直接推送，至少要求一次有写权限成员的审批和通过检查；日常集成可使用 `develop`。
4. C 开发分支建议使用 `algo/<方案简称>`，修复使用 `fix/<问题简称>`；从 `develop` 创建并通过 PR 合并。
5. 发布可复现实验版本时创建 Release Tag；记录 B 的来源 Tag 与 C 的自身 Tag，避免仅记录浮动分支名。

## 7. 主要风险与处理原则

| 风险 | 处理原则 |
| --- | --- |
| C 仓库体积增大 | 接受“完整派生代码”的空间代价，换取独立修改和可复现；不向 A 复制这些源码。 |
| B 后续更新 | 不自动同步到既有 C；需要升级时，由 C 创建明确的升级分支、重新测试并更新 `derived-from.yaml`。 |
| 两个 C 修改同一模块 | 不需要在运行时解决冲突，因为二者各自独立；需要合并思路时，人工创建新的 C。 |
| C 与平台接口不兼容 | 保持状态为 `draft/review`，向 A 提供最小接口需求；禁止为单个 C 私自改坏 Runner。 |
| 私有仓库无法下载 | 由 GitHub 仓库授权和本机 Git 凭据解决；平台不得保存或转发密钥。 |

## 8. 给开发者 A 的交付物

1. B 仓库地址、固定 Tag 和 40 位提交；
2. C 仓库地址、固定 Tag/提交和访问前提；
3. C 的 `algorithm.yaml` 与 `derived-from.yaml`；
4. 依赖文件、Python 要求、数据格式、训练入口和输出目录约定；
5. 已完成验证的命令与结论；
6. 未完成验证、失败原因和需要平台补充的最小接口。

只有以上资料齐全，开发者 A 才能将 C 标记为已登记、允许下载或进入平台训练预检；缺少资料的 C 只能保持草稿，不得向实验操作员宣传为可用算法。
