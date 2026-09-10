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

当前可实际执行的示例为“小目标检测 → 官方 Ultralytics YOLO11n 小目标基线 v1 → 小目标增强策略 v1”。MMDetection 与知识蒸馏目录中的包仍是协议示例；在其对应模块源码和配置合并器完成前，平台不会把它们暴露为可运行改进，避免只改配置却没有实际算法实现。

详细字段说明见 [algorithm-schema.md](algorithm-schema.md)。
