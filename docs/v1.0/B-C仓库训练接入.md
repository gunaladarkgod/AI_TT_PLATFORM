# B/C 仓库训练接入

## 创建任务

新建任务不再选择训练引擎或叠加改进包。选择“小目标检测”或“知识蒸馏”，再选择已下载的算法方案。B 名称以 `（baseline）` 结尾，C 为独立完整算法。当前接通 YOLO11s 基线和 YOLO11s-P234-TDSC；知识蒸馏尚无对应仓库时显示空列表，不回退到普通检测基线。

下拉选项来自 `GET /trainTask/component-options?direction=small-object`，复用研究基线公约中的 B/C 登记及本机状态。未下载不显示；已下载但缺少运行适配器或必要源码时置灰。下载或移除后重新打开表单、返回训练页窗口或点击“刷新已下载方案”更新列表。保存和启动均重新检查。

选择任务 Python 和 YOLO 格式实例数据集，调整允许参数，保存后通过原任务列表发布运行。模型配置由方案固定；默认预训练权重为 `yolo11s.pt`，首次训练须有该权重或能够取得它。训练 Python 需要满足仓库依赖（PyTorch 等）；不要求将环境中安装的官方 Ultralytics 替换成某个 C 仓库。

## 受控运行适配

`research-registry/training-adapters.json` 是平台审核的运行描述，包含方案 ID、类型、模型配置、引擎版本、默认值和参数范围。它不包含任意命令或本机绝对路径，不由浏览器提供。当前 B/C 缺少标准算法清单，采用这份显式适配描述接入；原公约页中的草稿/缺少清单提示仍保留，不自动改成 published。后续标准清单应在各独立仓库维护并审核。

当前两项使用各自仓库中的 Ultralytics 8.4.165。历史官方任务仍使用原 8.4.115 路径。C 直接加载自己的完整源码及 `yolo11s-p234-tdsc.yaml`，不叠加到本机 B，因此删除 B 不影响独立 C。P5 辅助监督、残差及空间等变体未在本次开放。

任务保存组件 ID、类型、完整提交和适配配置 SHA-256。Runner 启动前核对组件存在性、提交、已跟踪源码修改、模型配置、任务解释器依赖及实际导入位置。组件提交或适配配置变化需要重新编辑保存任务。排队或运行中的任务使用组件时，组件删除接口拒绝移除。手工删除源码不受平台保护。

统一 Worker 在独立子进程中将选定源码根目录置于模块搜索路径首位，检查和训练采用相同来源，不修改全局 Python 环境。数据及输出由平台注入，不执行仓库里写死个人路径的实验脚本。原任务的停止、日志、指标和结果保存机制继续使用。

运行目录 `ultralytics_run.json` 保存平台和算法提交、仓库 URL、运行描述快照、训练 Python 版本与实际导入位置、模型、数据集标识、最终参数及命令。运行文件可能包含本机路径，只保留在 artifacts，不提交源码库。

## 验证与部署

- 前端：`cd fronternd` 后运行 `npm run build`。
- 后端：运行 ComponentTrainingServiceTest、AlgorithmComponentServiceTest、ResearchRegistryServiceTest、ResearchCatalogServiceTest、TrainTaskServiceTest、TrainRunnerRetryTest、TrainRunnerInferenceTest。
- Runner：`python -m unittest discover -s runner`。
- 更新 Java/Python 后由运行人员重新加载后端及 Runner，再从网页验收任务创建、发布、停止和结果查询。

本次隔离冒烟验证使用合成图片、CPU、1 轮、随机初始化的 YOLO11s 权重，B/C 都执行真实训练并生成 best.pt/last.pt。它验证源码接入和训练产物，不是正式数据集评测，也不代表网页、数据库结果登记已完成端到端验收。证据位于本机 `artifacts/component_training_smoke/{baseline,tdsc}/smoke.log`，不纳入提交。
