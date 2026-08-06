# GitHub 协作流程

本文适用于 AI_TT_PLATFORM 的多人协作开发。目标是让每一项改动都可追溯、可审查，并避免多人直接修改主分支造成冲突。

## 1. 协作规则概览

- `main` 是稳定主分支：只接收已审查、可运行的代码，不直接在此分支开发。
- 每一个需求或修复都新建独立分支；分支完成后通过 Pull Request（PR）请求合并。
- 合并前至少完成一次前端构建或后端编译，以及与改动有关的功能自测。
- 不提交个人环境、运行产物或大数据：例如 `node_modules`、`target`、`logs`、`artifacts`、本地数据集和个人密码。

示例：要增加“训练结果导出”功能，建立 `feature/result-export` 分支；不要直接把改动提交到 `main`。

## 2. 首次下载主分支

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
```

`git clone` 下载仓库；`git switch main` 切换到主分支；`git pull origin main` 获取其他成员最新合并的代码。

首次使用 Git 的电脑还应设置提交署名：

```powershell
git config --global user.name "你的姓名"
git config --global user.email "你的邮箱"
```

## 3. 开发前同步主分支

### 这一环节是做什么的

在创建新分支前，先取得最新主分支，避免从过时的代码开始开发，减少后续冲突。

### 示例与操作

```powershell
git switch main
git pull origin main
git status
```

如果 `git status` 显示工作区有未提交修改，先提交、暂存（`git stash`）或确认不需要后再继续；不要在不明状态下直接拉取。

## 4. 为一个需求创建分支

### 这一环节是做什么的

分支相当于独立工作线。你在分支中修改不会影响其他人使用的 `main`，也便于将一个需求完整地放进一次 PR。

### 命名建议

- 新功能：`feature/功能名称`，如 `feature/result-export`
- 修复：`fix/问题名称`，如 `fix/login-auto-switch`
- 文档：`docs/文档名称`，如 `docs/github-workflow`
- 重构：`refactor/范围名称`，如 `refactor/dataset-refresh`
- 工程维护：`chore/事项名称`，如 `chore/update-dependencies`
- 尚未明确开发方向的临时探索：`dev/姓名简称`，如 `dev/gqy`

命名规则：

1. 一个分支只处理一个明确需求或问题，对应一个 PR。
2. 全部使用小写英文、数字和连字符（`-`）；不要使用空格、中文、下划线或个人姓名。
3. 前缀必须表达改动类型，斜杠后的名称表达功能，而不是笼统写成 `test`、`update` 或 `new`。
4. 有任务编号时可加入编号，例如 `feature/123-result-export`。

正确示例：`feature/result-export`、`fix/runner-startup`。不推荐：`guoqinyao-test`、`feature_结果导出`、`update`。

`dev/姓名简称` 只用于需求尚未明确时的个人探索、验证或原型开发。方向明确后，应从该分支整理出符合规范的 `feature/`、`fix/` 等分支再创建 PR；不要将模糊、混杂的 `dev/` 分支直接合并到 `main`。

### 示例与操作

```powershell
git switch -c feature/result-export
```

这条命令从当前最新的 `main` 创建并切换到新分支。开发期间可用 `git branch --show-current` 确认自己不在 `main`。

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

PR 不是立即合并，而是请求团队把你的分支合并到主分支。它提供代码差异、讨论、审查和自动检查的统一入口。

### 示例与操作

1. 打开 GitHub 仓库页面，通常会看到 **Compare & pull request** 按钮；也可进入 **Pull requests** → **New pull request**。
2. 确认 **base** 为 `main`，**compare** 为 `feature/result-export`。
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

审查通过且检查成功后，将分支代码合并到 `main`。合并后删除已经完成的分支，保持仓库整洁。

### 示例与操作

1. 在 PR 页面确认审查通过后，点击 **Merge pull request**。
2. 使用默认的合并方式即可；若团队另有约定，以团队约定为准。
3. 点击 **Delete branch** 删除远程功能分支。
4. 回到本地同步主分支并删除本地分支：

```powershell
git switch main
git pull origin main
git branch -d feature/result-export
```

### 分支何时可以删除

功能分支只是实现或修复某一项独立功能的临时工作线。确认 PR **已经合并**到 `main` 后，远程和本地的该功能分支都可以删除；合并后的代码和提交历史已经保留在 `main`，删除分支不会删除功能。

若 GitHub 没有自动删除远程分支，可执行：

```powershell
git push origin --delete feature/result-export
```

不要删除 `main`、团队约定的长期分支，或尚未合并/仍在审查的功能分支。若 `git branch -d` 拒绝删除，表示 Git 认为分支尚未合并；应先核对 PR 状态，不要改用强制删除。

## 10. 发生冲突时怎么办

冲突表示两个人修改了同一段代码，Git 无法自动判断该保留谁的内容。不要直接覆盖别人的版本。

示例：你的 `feature/result-export` 开发期间，其他人已合并了同一页面的筛选功能。先同步主分支：

```powershell
git switch feature/result-export
git fetch origin
git merge origin/main
```

若 Git 提示冲突，打开标有 `<<<<<<<`、`=======`、`>>>>>>>` 的文件，人工保留正确的两边逻辑，删除这些标记后执行：

```powershell
git add <已解决的文件>
git commit -m "merge: resolve conflicts with main"
git push
```

拿不准时不要强行解决，先在 PR 中说明冲突位置并与相关成员沟通。

## 11. 每次开发的完整流程与失败处理

以下流程适合每一个独立功能或修复。示例分支为 `feature/result-export`。

### 步骤 1：确认工作区干净

```powershell
git status
```

成功标准：显示 `working tree clean`，或你清楚每个未提交文件属于什么改动。

失败处理：若存在其他需求的修改，先提交到原分支，或使用 `git stash` 暂存；不要把不相关文件带入新功能分支。

### 步骤 2：同步稳定主分支

```powershell
git switch main
git pull origin main
```

成功标准：本地 `main` 已与 GitHub 的 `origin/main` 同步。

失败处理：若切换分支被未提交改动阻止，返回步骤 1；若拉取出现冲突，先停止开发、解决冲突并确认 `main` 可运行，再继续。

### 步骤 3：创建并确认功能分支

```powershell
git switch -c feature/result-export
git branch --show-current
```

成功标准：最后一条命令输出 `feature/result-export`。

失败处理：若提示分支已存在，使用 `git switch feature/result-export` 切换到已有分支，并先同步它与主分支的差异；不要重复创建同名分支。

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

失败处理：构建失败时先修复错误；不要为了提交而跳过失败。若失败与他人刚合并的代码有关，先将 `origin/main` 合并到当前分支，解决冲突后重新验证。

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

在 GitHub 创建 PR，确认：**base = `main`**、**compare = `feature/result-export`**。填写改动内容、验证方式和注意事项，指定审查人。

成功标准：PR 可正常比较差异、自动检查通过、审查人没有未解决评论。

失败处理：若 GitHub 提示存在冲突，按“发生冲突时怎么办”章节先把 `origin/main` 合并到功能分支，解决、验证、提交并推送；PR 会自动更新。

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
git switch main
git pull origin main
git branch -d feature/result-export
git push origin --delete feature/result-export
```

成功标准：`main` 包含功能，远程和本地临时功能分支均已清理。

失败处理：若删除远程分支提示不存在，通常表示 GitHub 已自动删除，无需处理；若本地删除被拒绝，先确认 PR 已合并到 `main`，未合并则保留分支继续处理。
