# AI 训练平台（AI_TT_PLATFORM）

面向目标检测数据集管理、实例数据集生成和 MMDetection 训练的一体化本地平台。

## 快速启动（Windows）

### 1. 准备环境

- JDK 17 或更高版本（项目按 Java 17 编译配置）。
- MySQL 8.0：默认连接 `127.0.0.1:3306`，数据库名 `ai_zm_master`，账号 `root`，密码 `123456`。
- Node.js 18 或更高版本。
- 可选：Redis 6+。未安装 Redis 时可使用内存降级模式。
- 训练功能还需要已安装 MMDetection 依赖的 Python 环境。

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

### 4. 启动 MMDet Runner（训练功能需要）

后端默认会尝试自动启动 Runner；也可以在“模型训练”页面点击 Runner 区域的“启动”。若需手动启动，在项目根目录执行：

```powershell
cd mmdet_run\mmdet_runner_srv
$env:RUNNER_PYTHON = "C:\\你的环境\\python.exe"
.\start_runner.cmd
```

Runner 启动成功后监听 `http://127.0.0.1:8009/health`。`start_runner.cmd` 会优先使用 `RUNNER_PYTHON`，其次尝试项目内环境、`.venv`、`%USERPROFILE%\.conda\envs\openmmlab` 与系统 Python。

## 目录约定

```text
data/
├─ instance_dataset_mid/  # 标签映射导出后的中间实例数据集
└─ instance_dataset/      # 预处理完成的最终实例数据集
mmdet_run/myfiles/template/   # 内置 MMDet 配置模板
mmdet_run/myfiles/modelcfg/   # 按任务生成的 config.py
artifacts/mmdet_runs/         # 训练日志、权重与运行产物
```

原始数据集可以位于任意本地路径；平台只保存其路径引用，不复制原始图片和标注。

## 文档

- [使用说明文档 v1.0](docs/v1.0/使用说明文档v1.0.md)
- [技术说明文档](docs/v1.0/技术说明文档.md)
- [GitHub 协作流程](docs/v1.0/GitHub协作流程.md)
