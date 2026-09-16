# GitHub 协作流程

本文适用于 AI_TT_PLATFORM 的多人协作开发。目标是让需求、问题、代码审查和发布过程均可追溯，避免直接修改稳定分支。

算法模板、Runner 适配、研究基线和改进包的具体规范请阅读独立文档：[研究算法与改进包开发规范](算法集成规范.md)。

## 1. 协作规则概览

- `main` 是稳定发布分支，只接收已经完成集成测试的 `develop`，不直接开发。
- `develop` 是开发集成分支。功能分支通过 PR 合并到此处，供团队集中测试。
- 每个功能、修复或文档改动使用独立分支，并关联一个 Issue（小型改动可在 PR 中说明原因）。
- PR 合并前至少完成一次前端构建或后端编译，以及与改动有关的手动验证。
- 不提交个人环境、运行产物或大数据，例如 `node_modules`、`target`、`logs`、`artifacts`、本地数据集、权重和密码。

示例：发现结果查询无法打开目录时，先建立 `bug` Issue；认领后创建 `fix/result-open-path`，完成修复并验证，再创建目标为 `develop` 的 PR，并在描述中写 `Refs #123`。PR 合并并完成集成验证后，再关闭 Issue。

## 2. Issue：记录问题与安排开发任务

### 2.1 Issue 是什么

Issue 是 GitHub 中用于记录待处理事项的卡片，不等同于代码分支或 PR。它可以用于：

- 报告 Bug：说明现象、复现步骤、期望结果、实际结果和截图/日志。
- 提出新功能或改进建议：描述使用场景、目标和验收条件。
- 发布与指派开发任务：维护者写清范围、优先级和验收标准，再指定负责人。
- 集中讨论：需求澄清、风险、设计决定和相关链接都保留在同一个 Issue 中。

不要把密码、令牌、数据库连接串、内部数据集路径等敏感信息放入公开 Issue。

### 2.2 推荐状态、标签与负责人

GitHub 原生 Issue 只有两种状态：

| 状态 | 含义 | 下一步 |
| --- | --- | --- |
| Open（打开） | 问题或任务尚未完成，可能还未认领、正在开发或等待验证 | 补充信息、指派负责人、创建分支或关联 PR |
| Closed（关闭） | 已完成、不再处理、重复或无法复现 | 写清关闭原因；若由 PR 修复，应保留 PR 链接 |

建议用标签细分 Open 状态，团队可在仓库的 **Issues → Labels** 中建立：

| 标签 | 用途 |
| --- | --- |
| `bug` | 已确认的异常或回归问题 |
| `feature` | 新功能需求 |
| `docs` | 文档改进 |
| `task` | 可直接执行的开发或维护任务 |
| `needs-triage` | 刚提交、尚未确认范围或优先级 |
| `in-progress` | 已被负责人认领，正在开发 |
| `blocked` | 被环境、外部依赖或待确认事项阻塞 |
| `needs-review` | 已有 PR，等待审查或验证 |
| `priority: high` / `priority: normal` | 优先级提示 |

负责人（Assignee）代表谁负责推动问题解决，不一定等于最终提交代码的人。一个 Issue 应有一位主要负责人；多人协作时可在评论中补充分工。

### 2.3 Bug 提交流程

1. 进入仓库 **Issues → New issue**，选择 Bug 模板或新建 Issue。
2. 标题写“模块 + 现象”，例如 `结果查询：打开路径提示 Runner 不可用`。
3. 描述至少包含：环境/版本、复现步骤、期望结果、实际结果、完整错误提示和截图或相关日志。
4. 添加 `bug`、优先级等标签；不确定责任人时先不指派，由维护者分派。
5. 维护者确认后补充验收条件，并将状态标签改为 `in-progress` 后开始开发。

若无法稳定复现，应保留在 Open 状态并标记 `needs-triage`；若确认重复，链接原 Issue 后关闭；若信息不足，可要求提问者补充后再继续。

### 2.4 发布和指派开发任务

维护者创建任务 Issue 时，应写清：

```markdown
## 目标
结果列表可按该条结果自身的目录读取日志。

## 范围
- 修改结果查询的日志定位逻辑
- 保留历史结果兼容提示

## 验收标准
- MMDet 与自定义结果均可读取各自日志
- Runner 未启动时显示明确指引
- 前端构建和后端编译通过
```

随后添加 `task` 或 `feature` 标签、优先级和负责人。开发者领取后：创建与 Issue 对应的分支、在 Issue 留言说明开始处理，并在 PR 描述中链接 Issue。

## 3. 首次下载并准备基础分支

第一次参与项目或换电脑时，在 GitHub 项目页点击 **Code**，复制 HTTPS 地址：

```powershell
git clone https://github.com/<组织或用户名>/AI_TT_PLATFORM.git
cd AI_TT_PLATFORM
git switch main
git pull origin main
git switch develop
git pull origin develop
```

`origin` 是本地给 GitHub 远程仓库的默认名称。`main` 用于稳定版本确认；日常功能分支从最新 `develop` 创建。

若仓库还没有 `develop`，仅由维护者执行一次：

```powershell
git switch main
git pull origin main
git switch -c develop
git push -u origin develop
```

首次使用 Git 的电脑还应设置提交署名：

```powershell
git config --global user.name "你的姓名"
git config --global user.email "你的邮箱"
```

## 4. 开发前同步 develop

```powershell
git switch develop
git pull origin develop
git status
```

成功标准是本地 `develop` 与远程同步，且工作区没有不明改动。若 `git status` 有未提交修改，先提交到原分支、使用 `git stash` 暂存，或确认不需要后再继续；不要在工作区混杂时直接拉取。

## 5. 创建与 Issue 对应的分支

分支是独立工作线，不会影响 `develop` 与 `main`。一个分支只处理一个清晰需求或问题，对应一个 PR。

| 改动类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 新功能 | `feature/功能名称` | `feature/result-export` |
| Bug 修复 | `fix/问题名称` | `fix/result-open-path` |
| 文档 | `docs/文档名称` | `docs/github-workflow` |
| 重构 | `refactor/范围名称` | `refactor/dataset-refresh` |
| 工程维护 | `chore/事项名称` | `chore/update-dependencies` |
| 算法基线或改进包开发 | `algo_算法简称` | `algo_small-object-augmentation` |
| 未明确方向的探索 | `dev/姓名简称` | `dev/gqy` |

除 `algo_算法简称` 外，名称使用小写英文、数字和连字符；不要使用空格、中文或笼统名称如 `test`、`update`。有任务编号时建议加入，例如 `fix/123-result-open-path`。

```powershell
git switch -c fix/123-result-open-path
git branch --show-current
```

`dev/姓名简称` 仅用于个人探索或原型。方向明确后应整理为符合规范的新分支再创建 PR；不要直接把混杂的 `dev/` 分支合并到长期分支。

## 6. 开发、验证与提交

完成一个逻辑完整的改动后，先检查变更范围：

```powershell
git status
git diff --check
git add <本次需求涉及的文件>
git commit -m "fix: resolve result path lookup"
```

不要使用 `git add .` 把日志、数据、生成配置或无关改动一并提交。提交前按改动执行验证：

```powershell
# 前端修改
cd fronternd
npm run build

# 后端修改（从项目根目录进入）
cd ..\backend
mvn -DskipTests compile
```

构建失败时应先修复，不要为了提交跳过失败。若失败来自刚合并的 `develop`，先把 `origin/develop` 合并到当前分支、处理冲突、重新验证后再继续。

若改动涉及 `research/`、训练引擎或 Runner，除前后端构建外，还应在 PR 中写明：研究方向/基线/改进包 ID、引擎与版本、数据格式、依赖/冲突、实际训练验证结果。只增加清单而没有实际模块或配置合并实现时，应明确标记为协议草稿，不得描述为“已接入训练”。

## 7. 上传分支并创建 PR

第一次上传分支：

```powershell
git push -u origin fix/123-result-open-path
```

之后本分支有新提交时只需 `git push`。在 GitHub 创建 PR 时确认：

- **base**：`develop`
- **compare**：当前功能分支
- 标题：如 `fix: 按结果目录读取训练日志`
- 描述：改动内容、验证方式、风险/注意事项，以及关联的 Issue。

建议在描述中写：

```markdown
Refs #123

## 改动内容
- 结果日志改为按结果目录读取

## 验证方式
- 前端 npm run build 通过
- 后端 mvn -DskipTests compile 通过
- 已验证 MMDet 与自定义结果
```

开发 PR 的目标是 `develop`，建议使用 `Refs #123` 建立关联。GitHub 的 `Fixes #123` 只会在 PR 合并到仓库**默认分支**时自动关闭 Issue；若默认分支是 `main`，合并到 `develop` 不会自动关闭。因此应在 `develop` 集成验证通过后，由负责人手动关闭 Issue 并留下 PR 链接和验证结论。

## 8. 审查、冲突与更新 PR

审查人会在 PR 中查看差异并留下评论。修改后继续提交并推送到同一分支，PR 会自动更新，无需重新创建。

研究相关 PR 的建议审查分工如下。该分工目前是协作约定，平台角色权限的强制控制需在后续单独实现；现阶段应通过 GitHub 仓库写权限、受保护的 `develop`/`main` 分支和 PR 审查执行。

| 固定角色 | 可负责的内容 | 不应直接修改的内容 |
| --- | --- | --- |
| `平台管理员` | 用户管理（平台内可修改其他用户权限等级）、前后端公共能力、统一 Runner、引擎适配、公共配置与最终审核 | 无，负责跨层集成 |
| `基线维护者` | 基线定义、源码、默认参数和实验规范 | 平台核心运行代码、其他人员的改进包 |
| `改进开发者` | 改进包、模块源码、参数和验证 | 基线定义、统一 Runner、平台核心代码 |
| `实验操作员` | 选择已开放基线/改进包、调整允许参数、创建任务和查看结果 | 研究源码、清单定义、平台核心代码 |

平台只采用上述四个固定角色，不设置按研究方向、基线或项目细分的范围授权。建议将 `平台管理员` 设为仓库管理员或维护者；`基线维护者`、`改进开发者` 通过功能分支和 PR 提交代码；`实验操作员` 默认不授予仓库写权限，只使用平台功能。后续平台内权限实现必须与此表保持一致。

若 GitHub 提示与 `develop` 冲突：

```powershell
git switch fix/123-result-open-path
git fetch origin
git merge origin/develop
```

人工处理冲突标记 `<<<<<<<`、`=======`、`>>>>>>>` 后：

```powershell
git add <已解决的文件>
git commit -m "merge: resolve conflicts with develop"
git push
```

拿不准冲突内容或审查意见时，不要猜测性覆盖；在 PR 或关联 Issue 中说明情况并请相关成员确认。

## 9. 合并、关闭 Issue 与清理分支

审查通过、检查成功且验收条件满足后，将 PR 合并到 `develop`。在关联 Issue 中确认集成验证已完成、留下 PR 链接和验证结论，再将 Issue 关闭；若后续发现回归，可重新打开同一 Issue 或建立新的 Bug Issue。

随后清理已完成的临时分支：

```powershell
git switch develop
git pull origin develop
git branch -d fix/123-result-open-path
git push origin --delete fix/123-result-open-path
```

功能分支在 PR 已合并到 `develop` 后可以删除，代码和提交历史仍保留在 `develop`。不要删除 `main`、`develop`、尚未合并或仍在审查的分支。若 `git branch -d` 拒绝删除，先核对 PR 是否真的已合并，不要强制删除。

## 10. 发布稳定版本：develop 合并到 main

当 `develop` 中的一组功能完成集成测试后，由维护者创建发布 PR：

1. 确认 `develop` 的前后端构建、关键流程验证和 PR 评论均已处理。
2. 创建 PR，设置 **base = `main`**、**compare = `develop`**。
3. 标题建议为 `release: <版本或日期>`，例如 `release: v1.1 dataset workflow`。
4. 合并后，`main` 成为新的稳定版本，`develop` 继续承接下一轮开发。

发布 PR 冲突必须回到 `develop` 解决、重新验证后再更新 PR；不要直接在 `main` 手工补代码。

## 11. 项目根目录说明

以下为当前项目根目录的目录用途。带点号的目录主要是本机或工具配置，通常不参与业务部署和提交。

| 目录 | 用途 | 版本管理建议 |
| --- | --- | --- |
| `backend/` | Spring Boot 后端、数据库迁移、业务接口 | 提交源码与迁移脚本；不提交 `target/` |
| `fronternd/` | Vue 3 + Vite 前端 | 提交源码与配置；不提交 `node_modules/`、`dist/` |
| `engines/mmdet_run/` | MMDetection、Python Runner、训练模板与生成配置目录 | 提交 Runner 与模板；不提交运行日志和生成配置 |
| `engines/yolo_run/` | 官方 Ultralytics 依赖版本与说明 | 提交版本约束与说明；由统一 Runner 调度，不提交数据、权重和运行输出 |
| `data/` | 中间实例数据集、最终实例数据集、预处理脚本等本地数据 | 运行数据，不提交 |
| `artifacts/` | MMDet、自定义算法、研究基线任务的日志、权重、运行规格、结果配置快照与元数据；研究任务位于 `artifacts/research/{方向}/{基线}/` | 运行产物，不提交 |
| `research/` | 研究方向、基线、改进包和项目内算法清单 | 提交算法定义与源码；不提交 `.runtime_cache/` |
| `logs/` | 后端主日志及历史归档日志 | 本机日志，不提交 |
| `docs/` | 使用、技术、协作与算法集成文档 | 提交 |
| `.git/` | Git 本地版本库数据 | Git 自动维护，不手工修改或提交 |
| `.agents/`、`.codex/` | 本机 AI 开发工具的工作配置与缓存 | 仅本机使用，通常不提交 |
| `.idea/`、`.vscode/` | IntelliJ IDEA、VS Code 的本机配置 | 通常不提交 |
| `.npm-cache/`、`.tmp/` | npm 与临时缓存 | 可按需清理，不提交 |

根目录的 `README.md` 是启动入口；`.gitignore` 规定哪些本机文件不进入 Git。新增顶级目录前，应先确定它是源码、配置、文档还是运行产物，并同步更新 `.gitignore` 与相关文档。
