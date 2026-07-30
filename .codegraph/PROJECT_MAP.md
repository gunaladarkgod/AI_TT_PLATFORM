# AI_TT_PLATFORM 项目地图

## 一句话定位

这是一个面向目标检测 / 计算机视觉训练流程的平台：管理数据集、标注引擎项目、训练任务、模型转换、文件资源，并通过独立 Python Runner 调用 MMDetection / YOLO 等训练环境。

## 顶层模块

| 路径 | 角色 | 当前理解 |
|---|---|---|
| `backend/` | 旧版主后端 | Spring Boot 3 + Java 17，当前业务主体，默认端口 `8081`。 |
| `fronternd/` | 前端 | Vue 3 + Vite + Element Plus，目录名疑似拼写为 `fronternd`；开发代理 `/develop` 指向旧后端 `8081`。 |
| `platform-v2/` | 新版后端骨架 | 新库 `ai_platform_v2`、双版本 API，默认端口 `8082`；已实现兼容旧前端的登录/JWT/Redis 鉴权骨架。 |
| `mmdet_run/` | MMDetection 运行区 | 包含 runner 服务、配置模板、日志、上传/训练文件，以及较大的第三方 MMDetection / MMPretrain 代码。 |
| `yolo_run/` | YOLO 运行区 | 包含 YOLOv5 运行代码，主要视为训练运行时依赖。 |
| `streamlit_app/` | 辅助工具 | 目前看到自有 Python 工具文件较少。 |
| `docs/` | 文档 | 有重构规格文档，但部分内容在当前终端显示为编码乱码；`platform-v2/README.md` 可正常阅读。 |

## 旧版后端 `backend/`

技术栈：

- Spring Boot `3.2.2`，Java `17`
- MyBatis-Plus + MySQL
- Redis，用于 JWT/token 索引等
- Shiro + Hutool JWT
- Knife4j / OpenAPI
- Flyway 依赖存在，但 `application.yml` 中默认关闭
- WebSocket、SSE、定时任务、异步任务

关键入口：

- 应用入口：`backend/src/main/java/com/xgls/web/WebApplication.java`
- 全局常量/状态：`backend/src/main/java/com/xgls/web/base/CodeMap.java`
- 配置：`backend/src/main/resources/application.yml`
- 控制器：`backend/src/main/java/com/xgls/web/controller`
- 服务层：`backend/src/main/java/com/xgls/web/service`
- 启动/队列/Runner 相关：`backend/src/main/java/com/xgls/web/runner`
- WebSocket：`backend/src/main/java/com/xgls/web/wscontroller`
- SSE：`backend/src/main/java/com/xgls/web/secontroller/SseController.java`
- DB 迁移：`backend/src/main/resources/db/migration`

主要业务域：

- 用户、菜单、角色菜单、用户项目权限
- 标注引擎项目/任务/标签，和 CVAT/Webhook 同步
- 原始数据集、任务数据集、实例数据集、数据集预览/拆分/合并
- 预处理脚本上传与执行
- 训练脚本、训练参数、训练任务、训练结果
- 训练队列、Runner 调用、TensorBoard URL
- YOLO 文件、模型转换、profile 配置
- 文件浏览、上传、删除、日志读取、目录下载

重要状态：

- 训练任务：`0=配置中`，`1=配置完毕`，`2=排队中`，`3=执行中`，`4=成功运行`，`5=配置错误`
- 模型转换：`0=准备好`，`3=进行中`，`4=结束`
- 导出任务：`0=默认`，`1=成功`，`2=排队`，`3=执行中`，`4=失败`

运行依赖与默认配置：

- 后端端口：`8081`
- MySQL：默认库 `ai_zm_master`
- Redis：默认 `127.0.0.1:6379`，库索引 `3`
- Runner 训练入口：`http://127.0.0.1:8009/api/runner/train`
- 大量默认路径偏 Linux，例如 `/home/omen1/AI_TT_Platform/...`，Windows 本地运行时需要用环境变量覆盖。

## 前端 `fronternd/`

技术栈：

- Vue 3 + Vite 4
- Element Plus
- Pinia
- Vue Router
- Axios
- AntV G2Plot、vxe-table、ace 等

关键入口：

- API 封装：`fronternd/src/api/api.js`
- Axios 拦截器与资源路径：`fronternd/src/api/axios.js`
- 路由：`fronternd/src/router/index.js`
- 状态：`fronternd/src/stores`
- 页面：`fronternd/src/views`

前后端关系：

- 开发模式下 `axios.defaults.baseURL = /develop`
- Vite 代理 `/develop -> http://127.0.0.1:8081`
- Token 放在 `Authorization` 请求头
- WebSocket 默认连旧后端 `/working/train/{uuid}` 和 `/working/trans/{uuid}`
- SSE 导出进度走 `/sse/export`

## 新版后端 `platform-v2/`

定位：

- 与旧 `backend/` 并行的新后端骨架
- 新数据库示例 `ai_platform_v2`
- `/api/v1/**` 用于兼容旧前端契约
- `/api/v2/**` 用于新规范 API

已实现/可见内容：

- Spring Boot 后端，默认端口 `8082`
- Flyway 默认开启，迁移脚本：`V1__v2_baseline_schema.sql`、`V2__seed_minimal.sql`、`V3__login_seed_user.sql`
- 登录、登出、Hutool JWT + Redis token 索引，兼容旧行为
- Spring Security 替代旧后端的 Shiro 方向

后续缺口：

- v2 登录契约和统一错误体
- 领域服务与 v1/v2 DTO 映射
- 旧库到新库的 ETL/迁移
- Runner/Webhook 客户端模块

## Python / Runner 区

自有 Python 文件目前主要看到：

- `mmdet_run/mmdet_runner_srv/mmdet_runner_server.py`
- `streamlit_app/utils/file_utils.py`

已生成 CodeGraph 产物：

- `.codegraph/python-codegraph.html`
- `.codegraph/python-codegraph.csv`

注意：`mmdet_run/mmdetection-3.0.0`、`mmdet_run/mmpretrain-1.2.0`、`yolo_run/yolov5` 更像第三方运行时源码，理解业务时默认先排除，避免噪声过大。

## 我之后开发前会默认先查的链路

### 后端业务改动

1. 先找对应 Controller 的路由前缀和方法。
2. 再追 Service、Mapper、Entity、XML mapper。
3. 看 `CodeMap.java` 中是否有状态码/路径常量约定。
4. 看 `application.yml` 是否依赖外部路径、Runner、Redis、CVAT 等配置。
5. 如果涉及训练/导出队列，必须看 `runner/TaskQueue`、`TrainQueueWorker`、`TrainRunnerService` 或 `ExportQueue`。

### 前端页面改动

1. 先看 `views/<页面>/index.vue`。
2. 再看 `src/api/api.js` 中对应请求函数。
3. 再对照后端 Controller。
4. 如果是导航/权限页面，还要看动态路由和菜单接口。

### v2 迁移/重构

1. 先看 `platform-v2/README.md` 与迁移脚本。
2. 明确旧接口契约是否需要 `/api/v1` 兼容。
3. 再决定是否实现 `/api/v2` 新契约。

## 当前风险/注意点

- 旧后端 `TrainTaskController` 体量很大，是未来维护风险点。
- 训练队列主要在内存里，多实例/重启场景要特别小心。
- Runner 调用偏同步阻塞，长任务更适合异步任务 + 回调/轮询。
- `application.yml` 存在大量 Linux 默认路径，Windows 本地开发要靠环境变量覆盖。
- 终端中部分中文显示乱码，可能是历史文件编码与当前控制台编码不一致；不要轻易批量转码，先确认文件真实编码。
- `application.yml` 中 `CVAT_API_' SERVER` 看起来像配置键拼写异常，后续涉及 CVAT 时要重点核验。
