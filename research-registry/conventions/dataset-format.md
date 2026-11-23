# 数据格式公约

登记算法必须明确声明 `data_format`。`coco` 使用 COCO 检测标注，`yolo` 使用 Ultralytics 的 `data.yaml` 和归一化检测标签，`custom` 仅用于已说明输入协议的外部命令。登记文件只描述兼容条件，不复制数据集，也不接受客户端传入的任意磁盘路径。
