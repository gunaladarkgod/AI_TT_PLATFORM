"""Single-file MMDetection configuration read/write service.

Templates under ``myfiles/template`` are complete MMEngine configs.  A task
configuration is always loaded through :class:`mmengine.config.Config`,
updated from UI values and dumped as one standalone ``config.py`` file.
"""

from __future__ import annotations

import re
import shutil
import sys
import types
from pathlib import Path
from typing import Any, Dict, Iterable, List, MutableMapping, Tuple


TEMPLATE_CATALOG: Dict[str, Tuple[str, str]] = {
    "Faster R-CNN": ("CNN", "rcnn/faster r-cnn.py"),
    "Cascade R-CNN": ("CNN", "rcnn/cascade r-cnn.py"),
    "DetectoRS": ("CNN", "rcnn/detectors.py"),
    "DETR": ("DETR", "detr/detr.py"),
    "Deformable DETR": ("DETR", "detr/deformable detr.py"),
    "DINO": ("DETR", "detr/dino.py"),
    "YOLOv3": ("YOLO", "yolo/yolov3.py"),
}


def _config_class():
    # This Windows environment's yapf parser can take minutes to import.
    # MMEngine only needs FormatCode when rendering pretty_text; the identity
    # formatter keeps Config.fromfile()/Config.dump() deterministic and fast.
    if "yapf" not in sys.modules:
        yapf = types.ModuleType("yapf")
        yapf.__version__ = "0.40.2"
        yapflib = types.ModuleType("yapf.yapflib")
        yapf_api = types.ModuleType("yapf.yapflib.yapf_api")
        yapf_api.FormatCode = lambda text, **_kwargs: (text, False)
        sys.modules.update({
            "yapf": yapf,
            "yapf.yapflib": yapflib,
            "yapf.yapflib.yapf_api": yapf_api,
        })
    from mmengine.config import Config
    return Config


def list_templates(upload_root: str) -> List[Dict[str, Any]]:
    root = Path(upload_root).resolve() / "template"
    result: List[Dict[str, Any]] = []
    for name, (group, relative) in TEMPLATE_CATALOG.items():
        path = (root / relative).resolve()
        result.append({
            "name": name,
            "group": group,
            "available": path.is_file() and root in path.parents,
            "relativePath": relative.replace("\\", "/"),
        })
    return result


def _safe_run_id(run_id: str) -> str:
    value = (run_id or "").strip()
    if not value or not re.fullmatch(r"[\w\-.\u4e00-\u9fff]+", value):
        raise ValueError("invalid task name for config directory")
    return value


def _template_path(upload_root: str, template_name: str) -> Path:
    item = TEMPLATE_CATALOG.get(template_name)
    if item is None:
        raise ValueError(f"unsupported template: {template_name}")
    root = Path(upload_root).resolve() / "template"
    path = (root / item[1]).resolve()
    if root not in path.parents or not path.is_file():
        raise FileNotFoundError(f"template not found: {path}")
    return path


def config_path(upload_root: str, run_id: str) -> Path:
    return Path(upload_root).resolve() / "modelcfg" / _safe_run_id(run_id) / "config.py"


def _as_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _as_float(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _milestones(value: Any) -> List[int]:
    if isinstance(value, (list, tuple)):
        return [int(v) for v in value]
    return [int(v) for v in re.findall(r"\d+", str(value or ""))]


def _prefix_img(value: Any) -> str:
    text = str(value or "").strip()
    match = re.search(r"img\s*=\s*['\"]([^'\"]+)['\"]", text)
    return match.group(1) if match else text


def _walk(value: Any) -> Iterable[MutableMapping[str, Any]]:
    if isinstance(value, MutableMapping):
        yield value
        for child in value.values():
            yield from _walk(child)
    elif isinstance(value, (list, tuple)):
        for child in value:
            yield from _walk(child)


def _set_num_classes(model: MutableMapping[str, Any], count: int) -> None:
    for node in _walk(model):
        if "num_classes" in node:
            node["num_classes"] = count


def _set_resize_scales(cfg: MutableMapping[str, Any], width: int, height: int) -> None:
    for node in _walk(cfg):
        node_type = str(node.get("type", ""))
        if "Resize" in node_type and "scale" in node:
            node["scale"] = (width, height)
    if "input_size" in cfg:
        cfg["input_size"] = (width, height)


def _set_dataset(dataset: MutableMapping[str, Any], data_root: str, ann_file: str,
                 img_prefix: str, classes: List[str]) -> None:
    # Some MMDet templates wrap the actual dataset (RepeatDataset/ConcatDataset).
    target = dataset
    while isinstance(target.get("dataset"), MutableMapping):
        target = target["dataset"]
    target["data_root"] = data_root
    target["ann_file"] = ann_file
    target["data_prefix"] = {"img": img_prefix}
    target["metainfo"] = {"classes": tuple(classes)}


def _set_attention_params(model: MutableMapping[str, Any], params: Dict[str, Any]) -> None:
    embed = _as_int(params.get("detr_embed_dims"), 256)
    heads = _as_int(params.get("detr_num_heads"), 8)
    attn_drop = _as_float(params.get("detr_attn_dropout"), 0.1)
    ffn_channels = _as_int(params.get("detr_ffn_channels"), 2048)
    ffn_num_fcs = _as_int(params.get("detr_ffn_num_fcs"), 2)
    ffn_drop = _as_float(params.get("detr_ffn_dropout"), 0.1)
    act = str(params.get("detr_ffn_act") or "ReLU")
    for node in _walk(model):
        if "embed_dims" in node:
            node["embed_dims"] = embed
        if "num_heads" in node:
            node["num_heads"] = heads
        if "attn_drop" in node:
            node["attn_drop"] = attn_drop
        if "dropout" in node and ("num_heads" in node or "embed_dims" in node):
            node["dropout"] = attn_drop
        if "feedforward_channels" in node:
            node["feedforward_channels"] = ffn_channels
        if "num_fcs" in node:
            node["num_fcs"] = ffn_num_fcs
        if "ffn_drop" in node:
            node["ffn_drop"] = ffn_drop
        if isinstance(node.get("act_cfg"), MutableMapping):
            node["act_cfg"]["type"] = act


def _apply_params(cfg: MutableMapping[str, Any], params: Dict[str, Any], dataset: Dict[str, Any]) -> None:
    width = _as_int(params.get("mmdet_input_width"), 1333)
    height = _as_int(params.get("mmdet_input_height"), 800)
    classes = [str(v) for v in dataset.get("classes", [])]
    class_count = _as_int(dataset.get("num_classes"), len(classes))
    data_root = str(dataset.get("data_root") or "").replace("\\", "/")
    if data_root and not data_root.endswith("/"):
        data_root += "/"

    cfg["data_root"] = data_root
    _set_num_classes(cfg["model"], class_count)
    _set_resize_scales(cfg, width, height)

    split_values = {
        "train": (dataset.get("ann_train"), dataset.get("prefix_train")),
        "val": (dataset.get("ann_val"), dataset.get("prefix_val")),
        "test": (dataset.get("ann_test"), dataset.get("prefix_test")),
    }
    for split, (ann, prefix) in split_values.items():
        loader = cfg.get(f"{split}_dataloader")
        if isinstance(loader, MutableMapping) and isinstance(loader.get("dataset"), MutableMapping):
            _set_dataset(loader["dataset"], data_root, str(ann or ""), _prefix_img(prefix), classes)
        evaluator = cfg.get(f"{split}_evaluator")
        if isinstance(evaluator, MutableMapping) and ann:
            evaluator["ann_file"] = data_root + str(ann).lstrip("/")

    train_loader = cfg.get("train_dataloader", {})
    if isinstance(train_loader, MutableMapping):
        train_loader["batch_size"] = _as_int(params.get("mmdet_batchsize"), train_loader.get("batch_size", 2))

    train_cfg = cfg.get("train_cfg", {})
    if isinstance(train_cfg, MutableMapping):
        train_cfg["max_epochs"] = _as_int(params.get("mmdet_epoch"), train_cfg.get("max_epochs", 12))
        train_cfg["val_interval"] = _as_int(params.get("mmdet_val_interval"), train_cfg.get("val_interval", 1))

    hooks = cfg.get("default_hooks", {})
    if isinstance(hooks, MutableMapping) and isinstance(hooks.get("checkpoint"), MutableMapping):
        hooks["checkpoint"]["interval"] = _as_int(params.get("mmdet_weight_interval"), hooks["checkpoint"].get("interval", 1))

    optimizer = cfg.get("optim_wrapper", {}).get("optimizer")
    if isinstance(optimizer, MutableMapping):
        opt_type = str(params.get("mmdet_opt") or optimizer.get("type") or "SGD")
        optimizer["type"] = opt_type
        optimizer["lr"] = _as_float(params.get("mmdet_inlr"), optimizer.get("lr", 0.001))
        if opt_type.lower() == "adamw":
            optimizer.pop("momentum", None)
            optimizer.setdefault("betas", (0.9, 0.999))
            optimizer.setdefault("weight_decay", 0.05)
        else:
            optimizer.pop("betas", None)
            optimizer.setdefault("momentum", 0.9)
            optimizer.setdefault("weight_decay", 0.0001)

    milestones = _milestones(params.get("mmdet_step"))
    if milestones:
        for scheduler in cfg.get("param_scheduler", []):
            if isinstance(scheduler, MutableMapping) and "milestones" in scheduler:
                scheduler["milestones"] = milestones

    model = cfg["model"]
    backbone = model.get("backbone")
    if isinstance(backbone, MutableMapping):
        if "depth" in backbone:
            backbone["depth"] = _as_int(params.get("mmdet_depth"), backbone["depth"])
        if params.get("checkpoint"):
            backbone["init_cfg"] = {"type": "Pretrained", "checkpoint": str(params["checkpoint"])}

    if isinstance(model.get("encoder"), MutableMapping) and "num_layers" in model["encoder"]:
        model["encoder"]["num_layers"] = _as_int(params.get("detr_encoder_layers"), model["encoder"]["num_layers"])
    if isinstance(model.get("decoder"), MutableMapping) and "num_layers" in model["decoder"]:
        model["decoder"]["num_layers"] = _as_int(params.get("detr_decoder_layers"), model["decoder"]["num_layers"])
    if isinstance(model.get("positional_encoding"), MutableMapping) and "temperature" in model["positional_encoding"]:
        model["positional_encoding"]["temperature"] = _as_int(params.get("detr_pos_temperature"), model["positional_encoding"]["temperature"])
    _set_attention_params(model, params)

    head = model.get("bbox_head")
    if not isinstance(head, MutableMapping):
        head = model.get("roi_head", {}).get("bbox_head")
    if isinstance(head, list) and head:
        heads = head
    elif isinstance(head, MutableMapping):
        heads = [head]
    else:
        heads = []
    for item in heads:
        for key, type_key, weight_key in (
            ("loss_cls", "detr_loss_cls_type", "detr_loss_cls_weight"),
            ("loss_bbox", "detr_loss_bbox_type", "detr_loss_bbox_weight"),
            ("loss_iou", "detr_loss_iou_type", "detr_loss_iou_weight"),
        ):
            loss = item.get(key)
            if isinstance(loss, MutableMapping):
                if params.get(type_key):
                    loss["type"] = str(params[type_key])
                if params.get(weight_key) is not None:
                    loss["loss_weight"] = _as_float(params[weight_key], loss.get("loss_weight", 1.0))


def generate_config(upload_root: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    run_id = _safe_run_id(str(payload.get("run_id") or ""))
    template_name = str(payload.get("template") or "")
    template = _template_path(upload_root, template_name)
    output = config_path(upload_root, run_id)
    output_dir = output.parent

    Config = _config_class()
    cfg = Config.fromfile(str(template))
    _apply_params(cfg._cfg_dict, dict(payload.get("params") or {}), dict(payload.get("dataset") or {}))

    # The task directory deliberately contains one and only one Python config.
    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    temporary = output_dir / "config.tmp.py"
    cfg.dump(str(temporary))
    temporary.replace(output)
    return {
        "run_id": run_id,
        "template": template_name,
        "template_path": str(template),
        "config_path": str(output),
        "file_name": output.name,
    }


def read_config(upload_root: str, run_id: str, include_text: bool = False) -> Dict[str, Any]:
    path = config_path(upload_root, run_id)
    if not path.is_file():
        raise FileNotFoundError(f"config not found: {path}")
    Config = _config_class()
    cfg = Config.fromfile(str(path))
    out: Dict[str, Any] = {
        "run_id": run_id,
        "config_path": str(path),
        "model_type": cfg.get("model", {}).get("type"),
        "max_epochs": cfg.get("train_cfg", {}).get("max_epochs"),
        "batch_size": cfg.get("train_dataloader", {}).get("batch_size"),
        "optimizer": dict(cfg.get("optim_wrapper", {}).get("optimizer", {})),
    }
    if include_text:
        out["text"] = path.read_text(encoding="utf-8")
    return out
