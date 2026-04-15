# AI_TT_Platform 重写规格说明（供 V2 新项目使用）

> **用途**：本文档从现有仓库（尤其 `backend/` Spring Boot 服务）抽象出**业务域、集成点、数据与状态约定、HTTP 路由面**，便于在另一套代码库中**干净地重做一版**，而不必反复翻旧代码。  
> **范围**：以当前实现为准做「行为级」描述；V2 技术选型可由你决定，文中仅给出**建议**与**必须兼容的外部契约**。

---

## 1. 产品定位

**一句话**：面向 **目标检测 / 计算机视觉** 的 **数据 → 预处理 → 训练 → 结果/转换** 全流程管理平台，与 **CVAT** 标注、**MMDetection（及 YOLO 等）** 训练环境、独立 **Python Training Runner** 协同。

**典型用户路径**（逻辑上）：

1. 管理用户、项目、菜单权限。
2. 管理 **原始数据集** → 生成 **任务数据集**（含划分）→ 进一步得到 **实例数据集**（单图条目、标注）。
3. 上传/维护 **预处理脚本**，对数据做转换。
4. 创建 **训练任务**（绑定脚本类型、数据集、超参等），进入 **排队**，由调度器拉起 **GPU/训练进程**（当前实现为 HTTP 调 Python Runner）。
5. 查看 **训练结果**、TensorBoard 链接；必要时做 **模型转换**（ONNX/RKNN 等 profile）。
6. 通过 **Webhook** 接收外部（如 CVAT）项目/标签变更并同步到本地库。

---

## 2. 系统边界

| 组件 | 职责 | 备注 |
|------|------|------|
| **本后端（Java）** | REST API、鉴权、元数据、任务状态、文件路径约定、队列编排、调用 Runner | 默认端口 `8081`（可配置） |
| **Python MMDet Runner** | 实际执行训练（MMDet）；与 Java 通过 HTTP 交互 | 默认 `http://127.0.0.1:8009`，路径如 `/api/runner/train`、`/health` |
| **MMDet / YOLO 运行时目录** | 配置模板、日志、上传物 | 如 `mmdet_run/myfiles`、`mmdet_run/logs` |
| **CVAT** | 标注与项目来源之一 | `sys.cvat.*` 配置 API；Webhook 推送事件 |
| **前端静态资源** | SPA 或静态站 | `spring.web.resources.static-locations` 指向本地目录 |
| **MySQL** | 业务持久化 | 库名示例 `ai_zm_master` |
| **Redis** | Token 等会话侧存储 | 与登录/JWT 配合 |

重写时建议：**明确划分「编排服务」与「训练执行服务」**，避免在 Java 进程内直接跑长训练命令（当前已是 HTTP 调用 Runner，方向正确）。

---

## 3. 技术栈（现状摘要）

- **Java 17**，**Spring Boot 3.2.x**
- **MyBatis-Plus** + **MySQL**
- **Spring Data Redis**
- **Apache Shiro（Jakarta）** + **JWT**（无 Session）
- **Knife4j**（OpenAPI 3）
- **Hutool**、**Lombok**
- **Flyway**（依赖存在；配置里常关闭，由自定义 `DatabaseInit` 等接管时需单独约定）
- **WebSocket**（`ServerEndpointExporter`）
- **SSE**（数据集导出等进度）
- **Spring Actuator**
- **Oshi**（硬件信息，可与许可证绑定）
- **BouncyCastle**（加解密）

**V2 可选方向（非强制）**：Spring Security 替代 Shiro；统一错误码与 Problem Details；OpenAPI 生成客户端；队列用 DB 悲观锁/Redis Stream 替代纯内存 `TaskQueue`（见下文风险）。

---

## 4. 核心领域模型（实体清单）

以下为 `entity` 包中的主要表/对象，重写时需保留**概念**（字段名可规范化）：

| 实体 | 说明 |
|------|------|
| `User`, `Menu`, `RoleMenu` | 用户、菜单、角色-菜单 |
| `UserProject` | 用户与项目关联 |
| `OriginalDataset` | 原始数据集 |
| `TaskDataset` | 任务数据集 |
| `InstanceDataset`, `InstanceDatasetinfo` | 实例数据集及逐条实例/图信息 |
| `AnnoInfo`, `ImgInfo` | 标注与图片辅助信息（按现有用法） |
| `EngineProject`, `EngineTask`, `EngineLabel` | 与外部引擎/CVAT 同步的项目、任务、标签 |
| `TrainScript` | 算法脚本元数据（名称含 `mmdet` 等用于分流） |
| `TrainTask` | 训练任务主表（见 §5） |
| `TrainData`, `TrainArgs`, `TrainExt`, `TrainLabel` | 训练数据与参数扩展 |
| `TrainResult` | 训练结果 |
| `TrainYoloFile` | YOLO 相关文件 |
| `ProfileTrain`, `ProfileTrans` | 训练/转换 profile |
| `ModelTrans` | 模型转换任务 |
| `PreprocessScriptInfo` | 预处理脚本信息 |
| `TemplateMapping` | 模板映射 |

---

## 5. 训练任务（TrainTask）关键字段与状态机

### 5.1 重要字段（现状）

- `name`：业务上常与 **runId** 等关联（Runner 使用 `runId` 查询参数调用）。
- `type`：关联 `TrainScript.id`（字符串形式）；脚本名 **`mmdet`** 时走 **TrainRunnerService → HTTP Runner**。
- `status`：**训练任务生命周期**（见 `CodeMap`）。
- `run_state`：运行结束是否成功（`TRAIN_FINISH_SUCCESS` / `TRAIN_FINISH_ERROR`）。
- `enqueue`：入队时间戳，作 **优先级** 依据。
- `username`：创建者。
- 统计：`cls_num`, `prj_num`, `task_num`, `img_num`, `obj_num`。
- `val_*`, `predict_*`：验证/预测子任务名称与状态。

### 5.2 训练任务 `status`（须与前端/脚本约定一致）

| 值 | 含义 |
|----|------|
| 0 | `TRAIN_TASK_STATUS_DEFAULT` 默认/配置中 |
| 1 | `TRAIN_TASK_STATUS_READY` 配置完毕 |
| 2 | `TRAIN_TASK_STATUS_QUEUE` 排队中 |
| 3 | `TRAIN_TASK_STATUS_RUN` 执行中 |
| 4 | `TRAIN_TASK_STATUS_FINISH` 成功结束 |
| 5 | `TRAIN_TASK_STATUS_CFG_FAIL` 配置失败 |

### 5.3 其它常用状态（摘录）

- **模型转换**：`MODEL_TRANS_STATUS_*`（0 就绪、3 运行、4 结束）。
- **导出任务**：`EXPORT_STATUS_*`（含排队、运行、失败）。
- **用户状态**：`USER_STATUS_LOCK` / `USER_STATUS_OK`。
- **用户类型**：`USER_TYPE_SYS`（系统管理员）、`USER_TYPE_OTHER`（普通用户）等。

### 5.4 训练调度（现状逻辑）

- 内存队列 **`TaskQueue`**：入队、优先级、`peekHeadId`、`takeIfHead`、最多约 100 个任务等。
- **`TrainQueueWorker`**：定时（约 2s）检查是否存在 `RUN` 任务；若无则尝试从队列取队首；若脚本为 **mmdet**，则 `TrainRunnerService.startByRunId(runId)` **同步 HTTP** 调 Runner，超时可达约 90 分钟量级。

**重写建议**：多实例部署时内存队列**不可靠**，应改为 **DB 锁 / 消息队列** 并明确「单集群同时 RUN 数」策略。

---

## 6. 磁盘与路径约定（CodeMap / application.yml）

业务大量依赖**可配置根路径**（环境变量覆盖），V2 应集中为 **PathConfig** 并校验目录存在性：

- `sys.original-dataset-root` → `original_dataset`
- `sys.task-dataset-root` → `task_dataset`
- `sys.instance-dataset-root` → `instance_dataset`
- `sys.root-upload`、`sys.root-logger`（MMDet 上传与日志）
- `sys.modelcfg.*`：本地默认 **MMDet config** 模板路径（faster-rcnn、cascade、dino、detr 等）
- `sys.instancecfg.*`：实例预处理脚本目录、Python 解释器路径
- `sys.runner.train-url`：Runner 训练入口，如 `http://127.0.0.1:8009/api/runner/train`
- `sys.runner.*`：`auto-start`、`launch-script` 等

目录片段常量（逻辑名）：`src`, `task`, `yolo`, `train`, `script`, `temp`, `images`, `labels`, `run`, `result`, `model_trans`, `data`, `original_dataset`, `annotations` 等（见 `CodeMap`）。

---

## 7. HTTP API 路由面（Controller 前缀）

以下为当前 **类级别** `@RequestMapping` 前缀，重写时可用于 **OpenAPI 模块划分**：

| 前缀 | 模块 |
|------|------|
| `/auth` | 登录、Token |
| `/user` | 用户 |
| `/menu` | 菜单 |
| `/userProject` | 用户项目 |
| `/original-dataset` | 原始数据集 |
| `/taskDataset` | 任务数据集 |
| `/instance` | 实例数据集 / 划分等（多控制器共用） |
| `/instanceDataset` | 实例数据集预览、图片、标注 |
| `/preprocess` | 预处理脚本上传与信息 |
| `/api/preprocess` | 预处理 API（与 `/preprocess` 并存） |
| `/api/template` | 模板 |
| `/trainTask` | 训练任务（**体量最大**） |
| `/trainScript` | 训练脚本 |
| `/trainLabel` | 训练标签 |
| `/trainResult` | 训练结果 |
| `/trainYolo` | YOLO 文件 |
| `/profile_train` | 训练 profile |
| `/profile_trans` | 转换 profile |
| `/modelTrans` | 模型转换 |
| `/engineProject` | 引擎项目 |
| `/engineTask` | 引擎任务 |
| `/files` | 文件服务 |
| `/api` | 对外 API：Webhook、Runner 健康检查等 |

**Shiro 匿名示例**（需在新系统中逐项评审）：`/auth/**`、`/sse/**`、`/api/**`、`/original-dataset/**`、`/taskDataset/**`、`/instanceDataset/**` 等。

---

## 8. 外部集成契约

### 8.1 Python Training Runner（必须）

- **健康检查**：`GET /health`（与 `train-url` 同 host/port，见 `TrainRunnerService.probeHealth`）。
- **启动训练**：`GET` 或约定方法访问 `sys.runner.train-url?runId=<encode>`，**同步**返回结果（Java 侧重试约 2 次）。
- **响应解析**：现有代码使用 `RunnerTrainResponse`，重写时需固定 **JSON 字段语义**（成功/失败、日志摘要、remark 追加规则等），建议单独出一页 **Runner API 契约**。

### 8.2 CVAT Webhook（`/api`）

`ApiController` 中 `webhook/project`：`event` 包括 `create:project`、`update:project`、`delete:project`、`create:label`、`update:label`、`delete:label` 等，正文为 JSON，由 `WebhookService` 落库同步。

另有 `webhook/task`（以代码为准）。

### 8.3 其它

- `sys.cpu_server`：CPU 相关 HTTP 服务（如有）
- TensorBoard：`sys.tensorboard.url`（前端拼接展示）

---

## 9. 安全与认证

- **JWT**：请求头 `Authorization`（常量 `CodeMap.X_ACCESS_TOKEN`）。
- **密码**：登录密码经 MD5（加盐 `CodeMap.XGLS`）等与库存比对。
- **许可证**：`sys.license` 文件；登录前校验 `LicenseUtil.LICENSE_STATUS`。
- **CORS**：当前 `WebApplication` 中全局 `CorsFilter` 较宽松；V2 应收紧。

---

## 10. 实时与异步

- **SSE**：`GET /sse/export`，`MediaType.TEXT_EVENT_STREAM`，`CustomEventListener` 提供 `Flux<ServerSentEvent<String>>`。
- **WebSocket**：`ServerEndpointExporter`，具体 `@ServerEndpoint` 类需在代码中检索。
- **定时任务**：`@EnableScheduling`（队列扫描、Token 清理 `TokenClean` 等）。
- **异步**：`@EnableAsync`（按需）。

---

## 11. 配置项索引（application.yml，便于 V2 迁移）

- `server.port`：`8081`
- `spring.datasource.*`：MySQL
- `spring.data.redis.*`
- `spring.web.resources.static-locations`：静态根、`MMDET_UPLOAD_ROOT`
- `mybatis-plus.mapper-locations`：`classpath:mapper/*.xml`
- `knife4j.*`
- `sys.license`, `sys.jwt-key`, `sys.enable-token`, `sys.encode-src`
- `sys.tensorboard.url`, `sys.cpu_server`
- `sys.cvat.*`（**注意**：现存配置中 `api-server` 键名可能有笔误，重写时应校验）
- `sys.conda`, `sys.*-root`, `sys.modelcfg.*`, `sys.instancecfg.*`, `sys.runner.*`
- `app.flyway.*`：与 `DatabaseInit` 联动时需统一开关语义

---

## 12. 与旧版对齐的验收清单（建议）

- [ ] 用户登录、JWT、匿名路径与业务路径区分正确
- [ ] 原始 / 任务 / 实例数据集 CRUD 与磁盘布局一致
- [ ] 预处理脚本上传与执行路径正确
- [ ] 训练任务创建 → 排队 → 运行 → 结束状态与统计字段正确
- [ ] `mmdet` 脚本走 Runner HTTP；非 mmdet 走约定脚本路径（若仍支持）
- [ ] Runner 不可用时的错误处理与任务 remark
- [ ] Webhook 项目/标签事件入库
- [ ] 模型转换、YOLO、引擎任务等核心路径
- [ ] 大文件上传限制（如 500MB）与静态资源映射
- [ ] SSE 导出进度
- [ ] 许可证与多实例部署策略（队列）

---

## 13. 已知痛点与 V2 设计建议（基于结构观察）

1. **单体巨型 Controller**（如 `TrainTaskController`）：应按 **用例/聚合根** 拆为多个类或 **application service** 层。
2. **内存队列**：多实例与重启会丢序或重复调度，应 **持久化队列状态** 或使用 **MQ**。
3. **同步 HTTP 阻塞 90 分钟**：调度线程可能被占满；建议 Runner **异步任务 + 回调/Webhook/轮询** 查状态。
4. **魔法数字与字符串**：状态、脚本类型应 **枚举 + DB check**。
5. **路径与配置散落**：统一 **配置模块**，避免硬编码绝对路径。
6. **安全默认值**：生产关闭 Knife4j 或加认证；收紧 CORS；Webhook **签名校验**。
7. **API 版本化**：`/api/v1` 便于前后端并行演进。

---

## 14. 文档维护

- 若 Runner、CVAT、或数据库表结构有变更，请同步更新 **§5、§8、§11**。
- 重写完成后可将本文档改为「架构决策记录（ADR）」索引，指向各子模块详细设计。

---

**生成信息**：基于仓库 `backend/` 源码与 `application.yml` 整理；具体 HTTP 方法名与请求体以各 Controller 及 Knife4j 导出为准。
