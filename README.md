# AI 训练平台（AI_TT_PLATFORM）

面向目标检测数据集管理、实例数据集生成与统一训练调度的一体化本地平台。当前支持 MMDetection、官方 Ultralytics 和外部自定义训练命令。

Linux 桌面环境可使用 [图形启动器](launcher/linux/README.md) 从应用菜单启动后端和 Runner，并通过后端打开构建好的前端网页。

平台核心提供前后端、统一 Runner、任务队列和数据集处理；`research/` 保存研究方向、可复现基线和可叠加改进包。引擎属于平台核心，基线引用引擎，改进包只能叠加到指定基线。

## 多仓库试点：B 基线到 C 独立算法

平台正在从“项目内基线与改进包”过渡到“平台 A + 基线 B + 独立算法 C”的方式。平台远程仓库只保存核心代码和算法登记信息；B、C 均是独立私有仓库，算法源码不会被提交回本仓库。

```text
B：AI_TT_BASELINE_SMALL_OBJECT_YOLO11
   固定的小目标 YOLO11/Ultralytics 基线起点
   main@8397ada12
        │
        └──派生──> C：AI_TT_ALGO_SMALL_OBJECT_TDSC
                         TDSC 完整算法实现
                         main@ae35a9a41
```

| 类型 | 当前试点仓库 | 职责 | 当前状态 |
| --- | --- | --- | --- |
| B 基线 | [AI_TT_BASELINE_SMALL_OBJECT_YOLO11](https://github.com/gunaladarkgod/AI_TT_BASELINE_SMALL_OBJECT_YOLO11) | 提供可追溯、可复用的 YOLO11 小目标检测代码起点。 | 已上传源码；尚未建立正式 Release Tag、算法清单和独立验证记录。 |
| C 算法 | [AI_TT_ALGO_SMALL_OBJECT_TDSC](https://github.com/gunaladarkgod/AI_TT_ALGO_SMALL_OBJECT_TDSC) | 从 B 的固定提交派生出的 TDSC 完整算法；它独立维护，不与其他 C 在运行时叠加。 | 已登记为草稿，并已提供受控下载与状态查询接口；C 仓库尚缺标准清单和 Runner 入口，未完成平台调度验证。 |

上述 C 的 `main@ae35a9a41` 以 B 的 `main@8397ada12` 为祖先。后续需要在 C 中补齐 `algorithm.yaml`、`derived-from.yaml`、依赖说明、训练入口和验证记录；B/C 的权重、数据集、日志、虚拟环境及 Git 凭据均不得提交。

### 第一目标：先完成 B/C 与平台 A 的最小连接

第一阶段不迁移全部算法、不删除 `engines/`，也不处理任意改进组合。只验证以下最小闭环：

1. 在 `research-registry/` 登记 TDSC C 的仓库地址、提交版本、Ultralytics 引擎、YOLO 数据格式和 B 来源；
2. 由平台管理员将已登记 C 下载到本机 `local-components/algorithms/`，并校验其清单；
3. 模型训练页面只从“已下载且清单匹配”的 C 中选择算法；
4. 使用现有统一 Runner 对所选 C、任务 Python 和 YOLO `data.yaml` 做预检，并完成一次训练启动、日志读取和结果定位验证。

目前第 1 步和第 2 步的后端接口已经开始实现；尚未以真实私有仓库完成下载验证，也未接入 Runner 执行。前端必须以接口返回的 `localStatus`、`trainingReady` 与 `blockers` 为准，不能因仓库已登记就显示“可训练”。在以上四步通过前，B/C 不能称为“平台已接入”或“已验证可训练”。详细分工见 [开发者 A 任务书](docs/v1.0/多仓库改造-开发者A-平台登记与受控算法下载.md)、[开发者 B 任务书](docs/v1.0/多仓库改造-开发者B-基线派生与独立算法仓库.md) 和[独立算法组件接口约定](docs/v1.0/按需组件接口约定.md)。

## 快速启动（跨平台图形启动器）

项目根目录提供图形启动器。macOS 使用原生 `启动AI训练平台.app`，Windows 使用 `启动AI训练平台.bat`，Linux 使用 `启动AI训练平台.sh`，直接双击对应入口即可，不需要手动输入启动命令。

macOS 分发包可从仓库分支下载 `launcher/macos/dist/启动AI训练平台-macOS.dmg`。打开 DMG 后运行其中的 App；首次启动时选择本地 `AI_TT_PLATFORM` 项目根目录，后续会记住该位置。

启动器会在窗口中检查 JDK、Maven、Node.js、MySQL，后台启动 Spring Boot、Python Runner 和 Vite，并在服务就绪后打开浏览器。macOS 原生启动器直接管理三个服务；Windows/Linux 启动器由后端自动拉起 Runner。日志写入 `logs/launcher/`，停止按钮只会停止本次启动器创建的进程。

### 1. 准备环境

- JDK 17 或更高版本（项目按 Java 17 编译配置）。
- MySQL 8.0：默认连接 `127.0.0.1:3306`，数据库名 `ai_zm_master`，账号 `root`，密码 `123456`。
- Node.js 18 或更高版本。
- 可选：Redis 6+。未安装 Redis 时可使用内存降级模式。
- 每个训练任务需要对应引擎的 Python 环境；Runner 使用独立的基础环境。使用 MMDetection 配置功能时，Runner 环境还需安装 `runner/requirements-mmdet.txt`。

首次启动如果 `fronternd/node_modules` 不存在，启动器会自动安装前端依赖；安装过程显示在启动日志中。

下面的命令仅用于开发维护或故障排查，日常启动不需要打开终端。

数据库、Redis、数据目录与 Runner 的默认配置在 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)。中间实例数据集和实例数据集均使用相对项目根目录的路径，迁移项目后无需修改绝对路径。

### 2. 首次初始化数据库并启动后端

在 IntelliJ IDEA 中打开 `backend`，运行 `com.xgls.web.WebApplication`。第一次启动时，在 Run Configuration 的 **Environment variables** 填入：

```text
DB_USERNAME=root;DB_PASSWORD=123456;APP_FLYWAY_ENABLED=true;APP_FLYWAY_MODE=migrate;APP_REDIS_REQUIRED=false;APP_REDIS_FALLBACK_MEMORY=true
```

这会执行数据库迁移并创建所需表结构。后端默认地址为 `http://127.0.0.1:8081`。

之后的日常启动可保留数据库账号配置；若 Redis 已正常运行，可去掉 `APP_REDIS_REQUIRED=false;APP_REDIS_FALLBACK_MEMORY=true`。迁移已完成后，可去掉 `APP_FLYWAY_ENABLED=true`。

也可以在 `backend` 目录运行：

```powershell
mvn spring-boot:run
```

### 3. 启动前端

```powershell
cd fronternd
npm install
npm run dev
```

浏览器打开终端显示的地址（通常为 `http://127.0.0.1:5173`）。开发环境下，前端会把 `/develop` 请求代理到后端 `8081` 端口。

### 4. 启动统一 Runner（训练功能需要）

后端默认会尝试自动启动 Runner；也可以在“模型训练”页面点击 Runner 区域的“启动”。若需手动启动，在项目根目录执行：

```powershell
cd runner
$env:RUNNER_PYTHON = "C:\\你的环境\\python.exe"
.\start_runner.cmd
```

Runner 启动成功后监听 `http://127.0.0.1:8009/health`。`runner/` 统一调度 MMDet、官方 Ultralytics 和外部自定义命令；基础依赖见 `runner/requirements.txt`。MMDet 模板读取与配置生成还需在 Runner Python 中安装 `runner/requirements-mmdet.txt`，不影响其他引擎。`start_runner.cmd` 优先使用 `RUNNER_PYTHON`，其次尝试新目录环境、旧目录环境、`openmmlab` 与系统 Python；各训练任务仍使用创建时指定的 Python。

## 项目根目录说明

```text
backend/       # Spring Boot 后端、数据库迁移与业务接口
fronternd/     # Vue 3 前端
runner/        # 统一训练调度、日志和配置服务
engines/       # 可扩展的训练引擎目录
  mmdet_run/   # MMDetection、模板与任务生成配置
  yolo_run/    # 官方 Ultralytics 依赖版本与说明（由统一 Runner 调度）
data/          # 中间实例数据集、最终实例数据集和预处理脚本
artifacts/     # result_metadata 保存结果索引；mmdet_runs、yolo_runs、custom 分别保存各引擎训练日志与权重，研究任务位于对应引擎目录下
research/      # 已接入的项目内研究方向、算法基线与改进包（迁移期保留）
research-registry/ # 平台核心的研究基线公约、算法登记协议与跨仓库索引
local-components/ # 管理员按需下载的独立算法 C；仅本机存在、受 .gitignore 忽略，不提交到平台仓库
logs/          # 后端主日志与历史归档
docs/          # 使用、技术、协作和算法集成文档
```

原始数据集可以位于任意本地路径；平台只保存其路径引用，不复制原始图片和标注。

`.git/`、`.idea/`、`.vscode/`、`.agents/`、`.codex/`、`.npm-cache/` 与 `.tmp/` 是 Git、编辑器或本机工具的配置/缓存目录，不属于业务源码；完整说明见 [GitHub 协作流程](docs/v1.0/GitHub协作流程.md#11-项目根目录说明)。

## 文档

- [平台使用与实验说明 v1.0](docs/v1.0/使用说明文档v1.0.md)
- [技术说明文档](docs/v1.0/技术说明文档.md)
- [GitHub 协作流程](docs/v1.0/GitHub协作流程.md)
- [选择性克隆指南](docs/v1.0/选择性克隆指南.md)
- [平台内组件下载与接口](docs/v1.0/按需组件接口约定.md)：在模型训练页检测、下载所需引擎和研究方向。
- [研究算法与改进包开发规范](docs/v1.0/算法集成规范.md)
- [研究基线公约与算法登记协议](research-registry/README.md)
- [多仓库改造任务书 A：平台登记与受控算法下载](docs/v1.0/多仓库改造-开发者A-平台登记与受控算法下载.md)
- [多仓库改造任务书 B：基线派生与独立算法仓库试点](docs/v1.0/多仓库改造-开发者B-基线派生与独立算法仓库.md)
