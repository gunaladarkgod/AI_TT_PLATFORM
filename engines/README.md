# 训练引擎目录

此目录集中放置平台可接入的训练引擎，每个引擎保持独立的源码、启动脚本、模板和运行时配置：

- `mmdet_run/`：MMDetection 3.0.0、MMDet Runner 与平台模板；训练输出仍写入项目根目录的 `artifacts/mmdet_runs/`。
- `yolo_run/`：独立的 YOLO / Ultralytics 运行目录；训练输出路径由对应运行模式或任务配置决定。

新增引擎时，在此目录创建新的 `<engine>_run/`，并通过平台的 Runner/适配层声明其启动方式和可用能力。不要将数据集、权重、训练输出或 Python 虚拟环境提交到版本库。
