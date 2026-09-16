# 官方 Ultralytics 运行时

该目录记录平台官方 Ultralytics 引擎的依赖版本，固定为 `ultralytics==8.4.115`。它不是独立 Runner，也不保存虚拟环境。

Ultralytics 引擎仅接受由实例数据集预处理导出的 YOLO 格式数据。平台会在创建或发布任务前检查数据格式；COCO 数据集需要先补充 YOLO 导出后才可使用该引擎。

平台只有一个统一 Runner（8009）。创建 Ultralytics 训练任务时必须指定已经安装上述依赖的 Python/Conda 路径；Runner 仅做参数校验、启动子进程、日志、停止和结果目录管理，不会创建或修改该环境。请勿把模型权重、数据集、虚拟环境或训练输出提交到版本库。
