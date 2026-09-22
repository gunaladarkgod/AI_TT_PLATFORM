import torch
from models.yolo import Model

def load_backbone_only(model, weights_path="yolov5s.pt"):
    """
    只从 yolov5s.pt 里拷贝 backbone (model.0 ~ model.9) 的权重，
    neck 和 Detect 全部保持随机初始化。
    """
    ckpt = torch.load(weights_path, map_location="cpu")
    # ckpt['model'] 是一个 nn.Module（带 .model 的 EMA 版本也类似），这里直接 float 再拿 state_dict
    state_dict = ckpt["model"].float().state_dict()

    model_state = model.state_dict()
    new_state = {}

    # backbone 层的前缀：model.0. ~ model.9.
    backbone_prefixes = [f"model.{i}." for i in range(10)]

    for k, v in state_dict.items():
        if any(k.startswith(p) for p in backbone_prefixes):
            if k in model_state and model_state[k].shape == v.shape:
                new_state[k] = v

    # strict=False，只加载我们提供的这些 key，其他层用随机初始化
    missing, unexpected = model.load_state_dict(new_state, strict=False)

    print(f"Loaded {len(new_state)} backbone tensors from {weights_path}")
    print(f"Missing keys (ignored): {len(missing)}")
    print(f"Unexpected keys (ignored): {len(unexpected)}")
    return model

if __name__ == "__main__":
    # 构建你的 yolov5s_p2 模型（4 个检测头）
    cfg = "models/yolov5s_p2.yaml"
    model = Model(cfg, ch=3, nc=80, anchors=None)

    # 只加载 backbone
    model = load_backbone_only(model, "yolov5s.pt")

    # 随便 print 一下确认
    print(model)

