# Platform V2 Backend

新目录、**新库**、**双版本 API** 的 Spring Boot 骨架，与旧 `backend/` 并行存在；业务功能需按域逐步从旧系统迁移。

## 约定

| 项 | 说明 |
|----|------|
| **数据库** | 独立库名示例：`ai_platform_v2`（`application.yml` 中 `DB_URL`） |
| **API 版本** | `/api/v1/**` 兼容旧前端契约（迁移中）；`/api/v2/**` 为新规范（Problem+JSON、资源命名等） |
| **迁移** | Flyway：`src/main/resources/db/migration/` |

## 登录模块

已实现与旧后端兼容的 **登录 / 登出 / JWT + Redis 鉴权**（v1 路径）。详见 **[docs/LOGIN_MODULE.md](docs/LOGIN_MODULE.md)**。运行前需 **MySQL** 与 **Redis**（与 `spring.data.redis` 配置一致）。

## 新库设计要点（相对旧库）

- **显式外键**：`eng_*`、`dat_*`、`trn_*` 等关系在库内可追踪。
- **用户与权限**：`usr_user` + `usr_role` + `usr_user_role` + `menu_item` + `role_menu`；项目级数据权限用 `usr_project_access` 指向 `eng_project`。
- **训练任务**：`trn_job` 为中心；`trn_job_scope` / `trn_job_label` 多对多替代宽表；`trn_job_config` / `trn_job_extra` 1:1 拆参数字段与 JSON。
- **实例数据**：`dat_instance_snapshot` 表达「一次划分/预处理结果」版本，避免单表混杂多版路径。
- **算法注册**：`trn_algorithm` 用 `(code, category)` 区分训练/转换/数据脚本。
- **密码**：新系统建议使用 **BCrypt**（不再使用 MD5+盐）。

表前缀：`usr_` 用户、`eng_` 标注引擎镜像、`dat_` 数据集管线、`trn_` 训练、`mdl_` 模型转换、`preprocess_script` 等。

## 本地运行

1. 创建空库：

   ```sql
   CREATE DATABASE ai_platform_v2 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

2. 启动 **Redis**（默认 `127.0.0.1:6379`，库索引见 `application.yml`）。

3. 配置环境变量（可选）：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_*`、`SERVER_PORT`（默认 `8082`，避免与旧后端 `8081` 冲突）。

4. 编译运行：

   ```bash
   cd platform-v2
   mvn -pl platform-v2-server spring-boot:run
   ```

5. 验证：

   - `GET http://localhost:8082/api/v1/ping`
   - `GET http://localhost:8082/api/v2/ping`
   - `GET http://localhost:8082/actuator/health`
   - `POST http://localhost:8082/auth/login`（表单 `username`/`pmd`，默认种子用户见 `docs/LOGIN_MODULE.md`）

## 后续工作（未在本骨架中实现）

- `/api/v2` 新版登录契约、Problem Details 全局错误体
- 领域服务与 `v1`/`v2` 各自 DTO 映射
- 从旧库到新库的 **ETL 或一次性迁移脚本**（按业务验收分表迁移）
- Runner / Webhook 客户端模块
