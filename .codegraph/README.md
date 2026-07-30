# CodeGraph workspace

本目录用于辅助理解 `AI_TT_PLATFORM` 项目。

## 已安装的工具

- `CodeGraph`：安装在 `.codegraph/.venv`，通过仓库内虚拟环境运行，不污染系统 Python。
- 适用范围：主要适合分析 Python 代码关系；本仓库的主业务是 Java Spring Boot + Vue，因此 CodeGraph 只是辅助工具，不是唯一来源。

## 常用命令（Windows PowerShell）

```powershell
$env:PYTHONUTF8='1'
.codegraph\.venv\Scripts\codegraph streamlit_app mmdet_run\mmdet_runner_srv --output .codegraph\python-codegraph.html
.codegraph\.venv\Scripts\codegraph streamlit_app mmdet_run\mmdet_runner_srv --csv .codegraph\python-codegraph.csv
```

说明：在 Windows 上建议显式开启 `PYTHONUTF8=1`，避免中文注释或 UTF-8 文件被默认 GBK 解码时报错。

## 我后续理解项目时的默认方式

1. 先看 `.codegraph/PROJECT_MAP.md` 和 `.codegraph/project-graph.mmd`，确认模块边界。
2. Java 后端优先从 `backend/src/main/java/com/xgls/web/controller`、`service`、`runner`、`base/CodeMap.java` 和 `application.yml` 入手。
3. 新版后端优先从 `platform-v2/README.md`、`platform-v2-server/src/main/java/com/xgls/platform/v2` 和 Flyway 迁移脚本入手。
4. 前端优先从 `fronternd/src/api/axios.js`、`fronternd/src/api/api.js`、`fronternd/src/router/index.js` 和 `views` 入手。
5. Python runner/脚本关系再用 CodeGraph 生成的 HTML/CSV 辅助查看。
