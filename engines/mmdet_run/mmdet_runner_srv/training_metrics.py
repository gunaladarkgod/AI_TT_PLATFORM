"""Read per-run scalar metrics without importing either training framework."""

import csv
import json
import math
from datetime import datetime
from pathlib import Path


MAX_POINTS = 500


def _number(value):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _metric(key, engine):
    name = key.strip()
    simple = name.lower().replace(" ", "")
    if engine == "ultralytics":
        if "loss" in simple:
            return "loss", name.replace("train/", "训练 ").replace("val/", "验证 ")
        if simple.startswith("lr/"):
            return "learning_rate", name
        if simple.startswith("metrics/"):
            return "accuracy", name.removeprefix("metrics/")
    else:
        if "loss" in simple:
            return "loss", name.removeprefix("train/")
        if simple in ("lr", "train/lr") or simple.endswith("/lr"):
            return "learning_rate", name
        if any(token in simple for token in ("map", "ap50", "ap75")):
            return "accuracy", name.removeprefix("val/")
    return None


def _finish(engine, axis, values, source):
    series = []
    for (group, key, label), steps in values.items():
        points = [{"step": step, "value": value} for step, value in sorted(steps.items())]
        if len(points) > MAX_POINTS:
            stride = math.ceil((len(points) - 1) / (MAX_POINTS - 1))
            points = points[::stride]
            if points[-1]["step"] != max(steps):
                points.append({"step": max(steps), "value": steps[max(steps)]})
        series.append({"group": group, "key": key, "label": label, "points": points})
    current = max((max(points) for points in values.values() if points), default=None)
    return {
        "engine": engine,
        "axis": axis,
        "current_step": current,
        "series": series,
        "updated_at": datetime.fromtimestamp(source.stat().st_mtime).astimezone().isoformat(timespec="seconds") if source else None,
        "message": "" if series else "尚无可绘制的训练指标",
    }


def read_ultralytics(work_dir):
    source = work_dir / "results.csv"
    if not source.is_file():
        return _finish("ultralytics", "epoch", {}, None)
    values = {}
    with source.open("r", encoding="utf-8-sig", errors="replace", newline="") as stream:
        lines = stream.readlines()
    if lines and not lines[-1].endswith(("\n", "\r")):
        lines.pop()
    if not lines:
        return _finish("ultralytics", "epoch", values, source)
    for row in csv.DictReader(lines):
        fields = {str(key).strip(): value for key, value in row.items() if key is not None}
        epoch = _number(fields.get("epoch"))
        if epoch is None:
            continue
        for key, raw in fields.items():
            definition = _metric(key, "ultralytics")
            value = _number(raw)
            if definition and value is not None:
                group, label = definition
                values.setdefault((group, key, label), {})[epoch] = value
    return _finish("ultralytics", "epoch", values, source)


def read_mmdet(work_dir):
    candidates = list(work_dir.glob("*/vis_data/scalars.json")) + list(work_dir.glob("vis_data/scalars.json"))
    if not candidates:
        return _finish("mmdet", "iteration", {}, None)
    source = max(candidates, key=lambda path: path.stat().st_mtime)
    records = []
    with source.open("r", encoding="utf-8", errors="replace") as stream:
        for line in stream:
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(record, dict):
                records.append(record)
    axis = "epoch" if any(_number(record.get("epoch")) is not None for record in records) else "iteration"
    values = {}
    for record in records:
        step = _number(record.get("epoch" if axis == "epoch" and "epoch" in record else "step"))
        if step is None:
            continue
        for key, raw in record.items():
            definition = _metric(key, "mmdet")
            value = _number(raw)
            if definition and value is not None:
                group, label = definition
                values.setdefault((group, key, label), {})[step] = value
    return _finish("mmdet", axis, values, source)


def read_training_metrics(work_dir: Path, engine: str):
    if engine == "ultralytics":
        return read_ultralytics(work_dir)
    if engine == "mmdet":
        return read_mmdet(work_dir)
    return {
        "engine": engine, "axis": "epoch", "current_step": None,
        "series": [], "updated_at": None,
        "message": "当前引擎未提供结构化训练指标",
    }
