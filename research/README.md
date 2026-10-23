# 研究算法目录

`research/` 用于保存平台内的研究方向、可复现基线及其改进包。它不承载平台核心代码，也不保存训练数据、权重、日志或本机下载缓存。

## 目录约定

```text
research/
├─ algorithm-schema.md
└─ {direction}/
   ├─ direction.yaml
   └─ {baseline-id}/
      ├─ algorithm.yaml
      ├─ README.md
      ├─ src/
      └─ improvements/
         └─ {improvement-id}/
            ├─ algorithm.yaml
            ├─ README.md
            └─ src/
```

基线目录是一个方向内可独立复现的冻结起点；改进包只能新增或覆盖自己的配置、源码和参数，不能直接改写基线文件。

## 当前状态

目录扫描、改进包组合校验和官方 Ultralytics 训练链路已接入：在训练任务中选择基线后，可勾选该基线下声明为可叠加的改进包。平台按 `stack.priority` 固定组合顺序，并在保存和启动前校验父子关系、依赖、冲突、覆盖路径及参数 key。

小目标检测方向提供官方 Ultralytics YOLOv5s、YOLOv5m 与 YOLO11n 三个基线；YOLOv5s/m 使用 Ultralytics 8.4.115 对应的 YOLOv5u 权重。YOLO11n 基线还可叠加小目标增强策略 v1。知识蒸馏方向提供 Ultralytics YOLOv5s、YOLOv5m、YOLO11n 三个检测效果对照基线；这些基线可正常执行标准 Ultralytics 检测训练，但不执行教师-学生蒸馏。该方向的 MMDetection DETR 与解耦蒸馏包仍是协议示例，尚未接入对应模块源码和配置合并器。

详细字段说明见 [algorithm-schema.md](algorithm-schema.md)。
