# 官方 Ultralytics 运行时

该目录预留给平台的官方 Ultralytics 引擎，固定依赖 `ultralytics==8.4.115`，与 MMDet Python 环境隔离。

Ultralytics 引擎仅接受由实例数据集预处理导出的 YOLO 格式数据。平台会在创建或发布任务前检查数据格式；COCO 数据集需要先补充 YOLO 导出后才可使用该引擎。

后续训练入口使用本目录的独立 Runner。请勿把模型权重、数据集、虚拟环境或训练输出提交到版本库。
