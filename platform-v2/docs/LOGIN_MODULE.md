# 登录模块说明（Platform V2）

本文档描述 `platform-v2-server` 中已从旧后端迁移的**登录 / 登出 / 鉴权**行为、配置与扩展点，便于联调与后续改造。

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| **与旧系统行为对齐** | 密码算法、JWT 形态、Redis 键设计、请求头名称与 v1 接口路径尽量一致，便于旧前端或脚本少改即用。 |
| **与 V2 新库配合** | 用户数据落在 `usr_user` / `usr_role` / `usr_user_role`，不再使用 `sys_user` 表名（需从旧库迁移数据时单独做 ETL）。 |
| **双版本 API** | 当前登录挂在 **v1 兼容路径**；未来可在 `/api/v2/auth/**` 增加 JSON Body、OAuth2、刷新令牌等，而不破坏 v1。 |

---

## 2. 涉及的主要组件

| 类 / 文件 | 职责 |
|-----------|------|
| `api.v1.auth.AuthController` | `login` / `logout` / `unauthorized`，同时映射 **`/auth/*`** 与 **`/api/v1/auth/*`**。 |
| `auth.crypto.LegacyPasswordHasher` | 与旧系统一致：`MD5(明文密码 + 盐后缀)`，盐后缀默认 `xglszm`（对应旧 `CodeMap.XGLS`）。 |
| `auth.jwt.LegacyJwtService` | 使用 **Hutool JWT** 生成/校验 HS256 Token；载荷字段 **`id`、`username`、`type`** 与旧 `JwtUtils` 一致。 |
| `auth.redis.TokenRedisStore` | Redis：`token:<Authorization 值>` → 当前 JWT 字符串；`uid:<用户 id>` → 该用户当前会话使用的 header 值集合。 |
| `auth.security.JwtAuthenticationFilter` | 替代旧 Shiro `JwtFilter` + `JwtRealm`：校验 Redis 与 JWT，并按旧逻辑**滑动续期** Redis TTL / 刷新 JWT。 |
| `auth.security.SecurityConfiguration` | Spring Security 6：无 Session、JWT 过滤器、公开路径白名单、CORS。 |
| `auth.user.UserAccountRepository` | 基于 `JdbcTemplate` 查询用户及角色列表。 |
| `api.v1.common.AjaxResult` / `ErrorCode` | 与旧 `AjaxResult` / `ErrorCode` 数值对齐（如认证失败 `code=2`）。 |

---

## 3. HTTP 接口

### 3.1 登录

- **URL**：`POST /auth/login` 或 `POST /api/v1/auth/login`
- **Content-Type**：`application/x-www-form-urlencoded`（与旧 Spring MVC 默认一致）
- **参数**：

| 参数 | 含义 |
|------|------|
| `username` | 用户名 |
| `pmd` | 明文密码（服务端会加盐哈希后与库中比对） |

- **成功响应**（`AjaxResult`）：

```json
{
  "code": 0,
  "msg": "请求成功",
  "data": "<JWT 字符串>"
```

客户端后续请求将 **`data` 原样放入请求头** `Authorization`（见下文）。

- **常见错误码**（节选，与旧版一致）：

| code | 含义 |
|------|------|
| 4 | 参数错误 |
| 7 | 用户名或密码错误 |
| 8 | 账户锁定（`usr_user.status != 1`） |

### 3.2 登出

- **URL**：`POST /auth/logout` 或 `POST /api/v1/auth/logout`
- **请求头**：`Authorization: <登录返回的 token>`（可选；无则直接成功）
- **行为**：删除 `token:*` 与 `uid:*` 中对应条目。

### 3.3 鉴权失败占位

- **URL**：`GET /auth/unauthorized` 或 `GET /api/v1/auth/unauthorized`
- **响应**：`code=2`，`msg=认证失败`（与旧 `ErrorCode.AUTH_FAILED` 一致）。

### 3.4 受保护接口

除白名单外，任意请求必须在头中携带有效 **`Authorization`**，否则返回 **401**，体为 JSON：`{"code":2,"msg":"认证失败","data":null}`。

**白名单（无需 Token）**包括：`/actuator/**`、`/api/v1/ping`、`/api/v2/ping`、上述登录/登出/unauthorized 路径。

---

## 4. 安全细节

### 4.1 密码存储

- 库字段：`usr_user.password_hash`
- 算法：**与旧系统相同** — `MD5(明文 + platform.auth.password-salt-suffix)`，默认后缀 `xglszm`
- **建议**：后续新用户可逐步改为 **BCrypt**；迁移期可保留双轨校验（未在本阶段实现）。

### 4.2 JWT

- 算法：**HS256**（Hutool 默认）
- 密钥：`platform.auth.jwt-secret`（默认与旧 `sys.jwt-key` 示例一致，可通过环境变量 `JWT_SECRET` 覆盖）
- 过期时间：`platform.auth.jwt-expire-seconds`（默认 86400 秒）
- 载荷：
  - `id`：用户主键（`Long`）
  - `username`：用户名
  - `type`：**兼容旧前端的整数** — `1` 表示具备 `ADMIN` 角色（系统管理员），`3` 表示普通用户（与旧 `CodeMap` 中用户类型一致）

### 4.3 Redis 会话模型（与旧版一致）

- `token:<header 字符串>` → 当前有效的 JWT 字符串（登录时 header 与 JWT 初值相同；滑动刷新后 value 可能更新，header 不变）
- `uid:<userId>` → `Set` of header 字符串，用于按用户撤销会话（登出时移除）

### 4.4 许可证（License）

旧系统在登录前校验 `LicenseUtil`。V2 通过配置关闭：

```yaml
platform:
  auth:
    license-required: false
```

若设为 `true`，当前实现会返回错误提示需接入许可证模块（占位）。

### 4.5 CORS

全局允许任意 Origin 模式、常用方法与头（开发友好）；生产环境应收紧。

---

## 5. 配置项（application.yml）

```yaml
platform:
  auth:
    jwt-secret: ${JWT_SECRET:xgls!123#hk}
    jwt-expire-seconds: ${JWT_EXPIRE_SECONDS:86400}
    password-salt-suffix: xglszm
    license-required: false

spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DB:3}
```

---

## 6. 数据库与种子用户

- 用户表：`usr_user`；角色：`usr_role`、`usr_user_role`
- Flyway **`V3__login_seed_user.sql`** 插入默认账号：
  - 用户名：`admin`
  - 明文密码：`admin123`（按旧规则哈希后写入 `password_hash`）
  - 绑定角色：`ADMIN`（`usr_user_role` 中 `role_id=1`）

**生产环境请立即修改密码或删除种子用户。**

---

## 7. 与旧后端的差异说明

| 项目 | 旧后端 | V2 当前实现 |
|------|--------|-------------|
| 安全框架 | Apache Shiro + JwtFilter | Spring Security 6 + 自定义 JWT 过滤器 |
| 用户表名 | `sys_user` | `usr_user` |
| 许可证 | 登录强校验 | 默认关闭，可配置 |
| 401 响应体 | 部分为纯文本 | 统一为 `AjaxResult` JSON（过滤器中） |

---

## 8. 后续可演进方向

1. **`/api/v2/auth/login`**：JSON Body（`username`/`password`）、统一 Problem Details 错误体。
2. **刷新令牌 / 双 Token**：与前端约定 `Authorization: Bearer <access>`。
3. **密码升级策略**：登录成功后将 MD5 迁移为 BCrypt 存库。
4. **与菜单权限**：在过滤器或方法级 `@PreAuthorize` 使用 `LoginPrincipal` 的 `ROLE_*`。

---

## 9. 联调示例（curl）

```bash
# 登录（默认端口 8082）
curl -s -X POST 'http://localhost:8082/auth/login' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'username=admin&pmd=admin123'

# 携带 token 访问受保护接口（示例：若已存在业务接口）
curl -s 'http://localhost:8082/api/v1/some-protected' \
  -H 'Authorization: <上一步 data 中的 token>'
```

---

*文档版本随 `platform-v2` 代码迭代，如有变更请以 `AuthController`、`SecurityConfiguration`、`JwtAuthenticationFilter` 为准。*
