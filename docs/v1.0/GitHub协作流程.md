# GitHub 协作流程

本文适用于 AI_TT_PLATFORM 的多人协作开发。目标是让每一项改动都可追溯、可审查，并避免多人直接修改主分支造成冲突。

## 1. 协作规则概览

- `main` 是稳定发布分支：只接收已完成集成测试的 `develop`，不直接在此分支开发。
- `develop` 是开发集成分支：已完成开发的功能先通过 PR 合并到这里，供团队统一测试。
- 每一个需求或修复都新建独立分支；分支完成后通过 Pull Request（PR）请求合并。
- 合并前至少完成一次前端构建或后端编译，以及与改动有关的功能自测。
- 不提交个人环境、运行产物或大数据：例如 `node_modules`、`target`、`logs`、`artifacts`、本地数据集和个人密码。

示例：要增加“训练结果导出”功能，建立 `feature/result-export` 分支，PR 的目标为 `develop`；待 `develop` 集成测试稳定后，再创建 `develop → main` 的发布 PR。

## 2. 首次下载并初始化基础分支

### 这一环节是做什么的

将 GitHub 上的项目完整下载到自己的电脑，并让本地目录与远程仓库建立关联。`origin` 是 Git 默认给远程 GitHub 仓库起的名字。

### 什么时候做

第一次参与项目，或换了一台电脑时执行一次即可。

### 示例与操作

在 GitHub 项目页面点击 **Code**，复制 HTTPS 地址，例如：

```powershell
git clone https://github.com/<组织或用户名>/AI_TT_PLATFORM.git
cd AI_TT_PLATFORM
git switch main
git pull origin main
git switch develop
git pull origin develop
```

`git clone` 下载仓库；`main` 用于稳定版本确认；`develop` 是日常开发分支，后续功能分支均从它创建。

若仓库刚开始采用本流程、尚不存在 `develop`，由项目维护者只执行一次：

```powershell
git switch main
git pull origin main
git switch -c develop
git push -u origin develop
```

其他成员不要重复创建 `develop`，只需切换并同步已有的远程分支。

首次使用 Git 的电脑还应设置提交署名：

```powershell
git config --global user.name "你的姓名"
git config --global user.email "你的邮箱"
```

## 3. 开发前同步 develop 分支

### 这一环节是做什么的

在创建新分支前，先取得最新 `develop`，避免从过时的集成代码开始开发，减少后续冲突。

### 示例与操作

```powershell
git switch develop
git pull origin develop
git status
```

如果 `git status` 显示工作区有未提交修改，先提交、暂存（`git stash`）或确认不需要后再继续；不要在不明状态下直接拉取。

## 4. 为一个需求创建分支

### 这一环节是做什么的

分支相当于独立工作线。你在分支中修改不会影响其他人使用的 `develop` 或 `main`，也便于将一个需求完整地放进一次 PR。

### 命名建议

- 新功能：`feature/功能名称`，如 `feature/result-export`
- 修复：`fix/问题名称`，如 `fix/login-auto-switch`
- 文档：`docs/文档名称`，如 `docs/github-workflow`
- 重构：`refactor/范围名称`，如 `refactor/dataset-refresh`
- 工程维护：`chore/事项名称`，如 `chore/update-dependencies`
- 算法模板集成：`algo_算法简称`，如 `algo_rtdetr`
- 尚未明确开发方向的临时探索：`dev/姓名简称`，如 `dev/gqy`

命名规则：

1. 一个分支只处理一个明确需求或问题，对应一个 PR。
2. 全部使用小写英文、数字和连字符（`-`）；不要使用空格、中文、下划线或个人姓名。
3. 前缀必须表达改动类型，斜杠后的名称表达功能，而不是笼统写成 `test`、`update` 或 `new`。
4. 有任务编号时可加入编号，例如 `feature/123-result-export`。

正确示例：`feature/result-export`、`fix/runner-startup`、`algo_rtdetr`。不推荐：`guoqinyao-test`、`feature_结果导出`、`update`。

`dev/姓名简称` 只用于需求尚未明确时的个人探索、验证或原型开发。方向明确后，应从该分支整理出符合规范的 `feature/`、`fix/` 等分支再创建 PR；不要将模糊、混杂的 `dev/` 分支直接合并到 `develop` 或 `main`。

### 示例与操作

```powershell
git switch -c feature/result-export
```

这条命令从当前最新的 `develop` 创建并切换到新分支。开发期间可用 `git branch --show-current` 确认自己不在 `main` 或 `develop`。

## 5. 开发、检查与提交

### 这一环节是做什么的

将一个逻辑完整、可说明的改动保存为 Git 提交（commit）。提交是可回溯的版本节点，也是 PR 审查的基本单位。

### 示例与操作

完成结果导出功能后：

```powershell
git status
git add fronternd/src/views/resultQuery/index.vue backend/src/main/java/com/xgls/web/controller/TrainResultController.java
git commit -m "feat: add training result export"
```

先用 `git status` 查看改了什么；`git add` 只选择本次需求涉及的文件；`git commit` 写清楚改动目的。不要使用 `git add .` 把日志、配置或无关改动一并提交。

提交前，按改动执行验证。例如前端修改后：

```powershell
cd fronternd
npm run build
```

后端修改后：

```powershell
cd backend
mvn -DskipTests compile
```

## 6. 上传分支到 GitHub

### 这一环节是做什么的

将自己的本地分支上传到 GitHub，其他成员才能查看代码、讨论并创建 PR。

### 示例与操作

```powershell
git push -u origin feature/result-export
```

第一次上传使用 `-u`，它会建立本地分支和远程分支的对应关系。以后本分支有新提交时，只需执行：

```powershell
git push
```

## 7. 创建 Pull Request（请求合并）

### 这一环节是做什么的

PR 不是立即合并，而是请求团队把你的分支合并到开发集成分支 `develop`。它提供代码差异、讨论、审查和自动检查的统一入口。

### 示例与操作

1. 打开 GitHub 仓库页面，通常会看到 **Compare & pull request** 按钮；也可进入 **Pull requests** → **New pull request**。
2. 确认 **base** 为 `develop`，**compare** 为 `feature/result-export`。
3. 标题写清目的，例如 `feat: 支持训练结果导出`。
4. 描述中写明：改了什么、如何测试、是否有风险或需要注意的配置。
5. 指定审查人后创建 PR。

建议描述模板：

```markdown
## 改动内容
- 结果查询页增加导出按钮
- 后端增加导出接口

## 验证方式
- 前端 npm run build 通过
- 手动导出一条训练结果成功

## 注意事项
- 不包含 artifacts 和本地数据集文件
```

## 8. 审查、修改与再次推送

### 这一环节是做什么的

审查人会在 PR 中查看代码差异并留下评论。根据评论修改后，只需继续提交并推送到同一分支，PR 会自动更新，无需重新创建。

### 示例与操作

审查人要求补充错误提示时：

```powershell
git add fronternd/src/views/resultQuery/index.vue
git commit -m "fix: add export failure message"
git push
```

随后在 GitHub PR 中回复评论，说明已处理并给出验证结果。

## 9. 合并并清理分支

### 这一环节是做什么的

审查通过且检查成功后，将功能分支代码合并到 `develop`。合并后删除已经完成的功能分支，保持仓库整洁。

### 示例与操作

1. 在 PR 页面确认审查通过后，点击 **Merge pull request**。
2. 使用默认的合并方式即可；若团队另有约定，以团队约定为准。
3. 点击 **Delete branch** 删除远程功能分支。
4. 回到本地同步 `develop` 并删除本地功能分支：

```powershell
git switch develop
git pull origin develop
git branch -d feature/result-export
```

### 分支何时可以删除

功能分支只是实现或修复某一项独立功能的临时工作线。确认 PR **已经合并**到 `develop` 后，远程和本地的该功能分支都可以删除；合并后的代码和提交历史已经保留在 `develop`，删除分支不会删除功能。

若 GitHub 没有自动删除远程分支，可执行：

```powershell
git push origin --delete feature/result-export
```

不要删除 `main`、`develop`、团队约定的其他长期分支，或尚未合并/仍在审查的功能分支。若 `git branch -d` 拒绝删除，表示 Git 认为分支尚未合并；应先核对 PR 状态，不要改用强制删除。

## 10. 将 develop 合并到 main（发布稳定版本）

当 `develop` 中的一组功能完成集成测试、可以作为稳定版本交付时，由项目维护者创建一个发布 PR：

1. 先确认 `develop` 已完成前后端构建、关键流程验证和已有 PR 评论处理。
2. 在 GitHub 创建 PR，确认 **base = `main`**、**compare = `develop`**。
3. 标题建议写为 `release: <版本或日期>`，例如 `release: v1.1 dataset workflow`。
4. 审查并合并后，`main` 即成为新的稳定版本；`develop` 保留，继续接收下一批功能。

若发布 PR 出现冲突，应在 `develop` 上解决冲突、重新测试后再更新发布 PR；不要直接在 `main` 手工补代码。

## 11. 发生冲突时怎么办

冲突表示两个人修改了同一段代码，Git 无法自动判断该保留谁的内容。不要直接覆盖别人的版本。

示例：你的 `feature/result-export` 开发期间，其他人已将同一页面的筛选功能合并到 `develop`。先同步开发集成分支：

```powershell
git switch feature/result-export
git fetch origin
git merge origin/develop
```

若 Git 提示冲突，打开标有 `<<<<<<<`、`=======`、`>>>>>>>` 的文件，人工保留正确的两边逻辑，删除这些标记后执行：

```powershell
git add <已解决的文件>
git commit -m "merge: resolve conflicts with develop"
git push
```

拿不准时不要强行解决，先在 PR 中说明冲突位置并与相关成员沟通。

## 12. 每次开发的完整流程与失败处理

以下流程适合每一个独立功能或修复。示例分支为 `feature/result-export`。

### 步骤 1：确认工作区干净

```powershell
git status
```

成功标准：显示 `working tree clean`，或你清楚每个未提交文件属于什么改动。

失败处理：若存在其他需求的修改，先提交到原分支，或使用 `git stash` 暂存；不要把不相关文件带入新功能分支。

### 步骤 2：同步开发集成分支

```powershell
git switch develop
git pull origin develop
```

成功标准：本地 `develop` 已与 GitHub 的 `origin/develop` 同步。

失败处理：若切换分支被未提交改动阻止，返回步骤 1；若拉取出现冲突，先停止开发、解决冲突并确认 `develop` 可运行，再继续。

### 步骤 3：创建并确认功能分支

```powershell
git switch -c feature/result-export
git branch --show-current
```

成功标准：最后一条命令输出 `feature/result-export`。

失败处理：若提示分支已存在，使用 `git switch feature/result-export` 切换到已有分支，并先同步它与 `develop` 的差异；不要重复创建同名分支。

### 步骤 4：开发并进行本地验证

在功能分支完成代码修改后，根据改动类型执行验证：

```powershell
# 前端修改
cd fronternd
npm run build

# 后端修改（回到项目根目录后执行）
cd ..\backend
mvn -DskipTests compile
```

成功标准：构建或编译通过，并完成与功能有关的手动验证。

失败处理：构建失败时先修复错误；不要为了提交而跳过失败。若失败与他人刚合并的代码有关，先将 `origin/develop` 合并到当前分支，解决冲突后重新验证。

### 步骤 5：检查并创建提交

```powershell
git status
git diff --check
git add <本次需求涉及的文件>
git commit -m "feat: add training result export"
```

成功标准：`git status` 只包含预期文件，`git diff --check` 没有空白错误，提交信息说明了改动目的。

失败处理：若发现日志、数据集、`artifacts` 或其他无关文件，不要加入暂存区；使用 `git restore --staged <文件>` 取消暂存后再提交。若提交信息写错但尚未推送，可用 `git commit --amend` 修正。

### 步骤 6：上传功能分支

```powershell
git push -u origin feature/result-export
```

成功标准：GitHub 仓库页面能看到同名分支。

失败处理：若提示认证失败，检查 GitHub 登录、个人访问令牌或 SSH 密钥；若提示远程分支已更新，先 `git pull --rebase origin feature/result-export`，处理完成后再次推送。

### 步骤 7：创建并审查 PR

在 GitHub 创建 PR，确认：**base = `develop`**、**compare = `feature/result-export`**。填写改动内容、验证方式和注意事项，指定审查人。

成功标准：PR 可正常比较差异、自动检查通过、审查人没有未解决评论。

失败处理：若 GitHub 提示存在冲突，按“发生冲突时怎么办”章节先把 `origin/develop` 合并到功能分支，解决、验证、提交并推送；PR 会自动更新。

### 步骤 8：根据评论更新 PR

```powershell
git add <修改文件>
git commit -m "fix: address review feedback"
git push
```

成功标准：新提交自动出现在原 PR，评论被回复且标记为已解决。

失败处理：若不理解评论或改动可能扩大范围，先在 PR 讨论确认，不要猜测性修改。

### 步骤 9：合并、同步与删除分支

PR 合并后：

```powershell
git switch develop
git pull origin develop
git branch -d feature/result-export
git push origin --delete feature/result-export
```

成功标准：`develop` 包含功能，远程和本地临时功能分支均已清理；待本轮集成测试完成后，再按第 10 节将 `develop` 合并到 `main`。

失败处理：若删除远程分支提示不存在，通常表示 GitHub 已自动删除，无需处理；若本地删除被拒绝，先确认 PR 已合并到 `develop`，未合并则保留分支继续处理。

## 13. 算法集成规范

算法模板、Runner 适配和前端配置界面属于同一项功能，必须使用独立分支 `algo_算法简称` 开发，例如 `algo_rtdetr`。该分支的 PR 仍先合并到 `develop`，完成训练验证后再随 `develop` 发布到 `main`。

### 13.1 通用约定

1. 所有路径必须相对项目根目录，不能写入开发者电脑的绝对路径。
2. 每个算法必须有最小可运行样例：一个可用数据集、一次启动记录、可读取的 `train.log` 和明确的输出目录。
3. 训练输出统一放在 `artifacts/<算法类别>/`；MMDet 原始模式保持在 `artifacts/mmdet_runs/`，固定模式/自定义算法默认使用 `artifacts/custom/` 或该算法专属子目录。
4. 不提交数据集、权重、日志和按任务生成的配置；它们均属于本机运行产物。
5. 新增算法时，PR 描述必须说明：依赖环境、启动命令、输入数据格式、输出文件、停止训练方式和验证结果。

### 13.2 以 MMDetection 方式集成算法

当前 MMDet Runner 对接的是 `mmdet_run/mmdetection-3.0.0`，配置读写通过 MMEngine `Config` 完成。

#### 添加模板文件

1. 在 `mmdet_run/myfiles/template/` 下按类型新增一个完整 Python 配置文件，例如：

```text
mmdet_run/myfiles/template/detr/rt-detr.py
```

2. 在 `mmdet_run/mmdet_runner_srv/mmdet_config_service.py` 的 `TEMPLATE_CATALOG` 中登记显示名称、算法分组和相对文件路径。例如：

```python
"RT-DETR": ("DETR", "detr/rt-detr.py"),
```

只新增文件而不登记目录，前端不会显示该模板；目录接口 `GET /api/config/templates` 只返回目录表中的条目。

3. 模板必须是能被 `mmengine.config.Config.fromfile()` 单独读取的完整配置，包含模型、COCO 数据集、训练策略、评估器和运行时配置。任务创建后，系统会生成：

```text
mmdet_run/myfiles/modelcfg/{任务名称}/config.py
```

该文件由模板复制并写入用户设置，不应手工作为公共模板修改。

#### 在前端新增或调整配置项

前端入口为 `fronternd/src/views/trainTask/index.vue`。新增一个配置项时，必须同时完成以下对应关系：

1. 在创建/编辑任务表单中添加控件，并为仅适用的模板增加显示条件。
2. 在前端参数对象和提交载荷中加入字段；字段名称应表达其对应的 MMDet 配置路径。
3. 在 `mmdet_config_service.py` 的 `template_defaults()` 中，从模板配置读取默认值，保证切换模板和打开编辑页时能回填。
4. 在 `generate_config()` 及其写入辅助函数中，将该字段写回准确的配置路径。例如“主干网络名称”对应 `model.backbone.type`。
5. 创建任务、切换模板、编辑已有任务三种场景都要验证：默认值正确、保存后重开不丢失、生成的 `config.py` 可被 MMDet 读取。

不要只在前端显示一个字段而不写入 `config.py`，也不要只修改模板文件而不提供可读取的默认值。

#### MMDet 版本限制

当前模板受 MMDetection **3.0.0**、当前 Runner Python 环境和 MMEngine 配置语法限制。新增模板前必须在该环境中执行 `Config.fromfile()` 和一次最小训练验证。

- 不直接复制 MMDetection 2.x 配置：其 `data`、`runner`、`optimizer_config` 等写法与 3.x 不兼容。
- 不假设新版 MMDetection 的模块、注册名或配置字段已经存在；需要额外依赖时，必须先安装到 Runner 使用的 Python 环境。
- 依赖自定义 Python 模块时，应在模板中明确 `custom_imports`，并将源码随项目提交，不能依赖开发者本机路径。

### 13.3 YOLO 的两种接入方式

#### 方式 A：作为 MMDetection 模板

当前 `mmdet_run/myfiles/template/yolo/yolov3.py` 即属于此方式。它与其他 MMDet 模板完全相同：使用 `original` Runner 模式、生成并可读取/写入单一 `config.py`、通过 MMDet 的 COCO 数据集和训练命令执行。

优点是能够复用模板列表、前端参数回填和配置读写接口；限制是同样受 MMDetection 3.0.0 与其 YOLO 实现支持范围约束。

#### 方式 B：使用 Ultralytics

Ultralytics YOLO 应作为独立运行时放在项目根目录 `yolo_run/` 下，例如 `yolo_run/ultralytics/`，并使用固定 Runner 模式运行其官方训练命令。

Ultralytics 方式**不支持平台的配置文件读写功能**：不调用 MMDet 的模板、默认值、生成或读取接口，也不生成 `mmdet_run/myfiles/modelcfg/{任务名称}/config.py`。其 `data.yaml`、模型 YAML 和命令行参数由 Ultralytics 自身管理；前端只能保存并展示固定模式运行参数，不能将页面字段自动写回 Ultralytics 配置文件。

因此，选择 Ultralytics 时应在前端明确提示“配置文件由算法运行时自行管理”，并提供 Python 路径、执行目录、命令行、工作目录等运行参数，而非复用 MMDet 的配置编辑区。

### 13.4 集成其他模型类别

除 MMDet 和 YOLO 外，每个新类别在项目根目录建立独立运行目录：

```text
xxx_run/
├─ README.md              # 依赖、数据格式、启动方式和输出说明
├─ requirements.txt       # 算法专属 Python 依赖
├─ tools/train.py         # 可由 Runner 调用的训练入口
└─ templates/             # 可选：算法自身模板，不接入 MMDet Config 时仅供算法读取
```

前端新增模型类别时，使用 `自定义`/固定 Runner 模式并保存以下参数：

| 参数 | 含义 | 示例 |
| --- | --- | --- |
| `fixed_python_path` | 训练解释器 | `.conda/envs/xxx/python.exe` |
| `fixed_exec_dir` | 命令执行目录 | `xxx_run` |
| `fixed_command_line` | 训练命令，支持 `{run_id}`、`{work_dir}` | `tools/train.py --run-id {run_id} --work-dir {work_dir}` |
| `fixed_work_root` | 结果根目录 | `artifacts/xxx` |

路径均使用相对项目根目录的写法；Runner 在运行时解析它们。训练脚本必须将标准输出/错误输出写到控制台，Runner 会采集到 `train.log`，从而供训练日志和结果查询页面复用。

### 13.5 Runner 接口约定

现有 Runner 地址为 `http://127.0.0.1:8009`。新算法优先复用以下接口，不应另建一套无关的日志或停止机制：

| 接口 | 用途 | 新算法的要求 |
| --- | --- | --- |
| `POST /api/runner/train?runId=...` | 创建运行目录并启动训练 | 固定模式传入 `runner_mode=fixed` 与四个 `fixed_*` 参数；原始 MMDet 模式使用 `runner_mode=original`。|
| `POST /api/runner/stop?runId=...` | 停止进程树 | 训练入口必须由 Runner 启动，不能脱离 Runner 再自行后台启动子进程。|
| `GET /api/runner/log/latest?runId=...` | 读取最新日志 | 训练过程必须持续输出可读文本，建议包含 epoch、loss、mAP 和完成/失败标记。|
| `GET /health` | Runner 健康检查 | 集成前和发布前均应确认返回正常。|

MMDet 配置辅助接口仅供 MMDet 模板使用：`GET /api/config/templates`、`GET /api/config/template/defaults`、`POST /api/config/generate`、`GET /api/config/read`。自定义/Ultralytics 算法不得伪装成 MMDet 配置来调用它们。

### 13.6 算法集成 PR 验收清单

- [ ] 分支名为 `algo_算法简称`，目标 PR 为 `develop`。
- [ ] 所有新增路径均为相对路径，未提交权重、数据集、日志和本机环境。
- [ ] MMDet 模板已登记、可读默认值、可写 `config.py`，并在 3.0.0 环境验证。
- [ ] Ultralytics/其他自定义算法没有误用 MMDet 配置读写接口。
- [ ] 创建、发布、查看日志、停止训练、查看结果至少各验证一次。
- [ ] PR 说明依赖版本、命令、数据格式、输出位置和验证记录。
