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

本目录当前提供算法清单协议与“小目标检测”“知识蒸馏”两个草稿示例。它们用于目录扫描和前端联调，尚未接入训练执行流程；只有后续被标记为 `published` 并通过执行适配后，才会出现在普通训练人员的算法选择列表中。

详细字段说明见 [algorithm-schema.md](algorithm-schema.md)。
