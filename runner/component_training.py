"""Trusted B/C adapters shared with the backend; no client-supplied code paths."""
import hashlib
import json
import math
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def revision(path):
    try:
        return subprocess.check_output(["git", "-c", f"safe.directory={path.as_posix()}", "rev-parse", "HEAD"], cwd=path, text=True, timeout=10, stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.SubprocessError) as e:
        raise ValueError("Cannot read repository version") from e


def resolve_component(payload, root=ROOT):
    configuration = (root / "research-registry/training-adapters.json").read_bytes()
    if hashlib.sha256(configuration).hexdigest() != payload.get("adapter_digest"):
        raise ValueError("Training adapter changed; edit and save the task again")
    adapters = json.loads(configuration)
    adapter = next((x for x in adapters if x["id"] == payload.get("component_id") and x["kind"] == payload.get("component_kind")), None)
    if not adapter:
        raise ValueError("Unregistered training component")
    base = (root / "local-components").resolve()
    folder = "baselines" if adapter["kind"] == "baseline" else "algorithms"
    path = (base / folder / adapter["id"]).resolve()
    if not path.is_relative_to(base) or not (path / ".git").is_dir() or not (path / "ultralytics/__init__.py").is_file():
        raise ValueError("Training component missing or invalid")
    if revision(path) != payload.get("component_commit"):
        raise ValueError("Component version changed; edit and save the task again")
    if subprocess.run(["git", "-c", f"safe.directory={path.as_posix()}", "diff", "--quiet", "HEAD", "--"], cwd=path, timeout=10).returncode:
        raise ValueError("Component has uncommitted source changes")
    adapter["source_root"] = str(path)
    if adapter["model"].endswith(".yaml"):
        model = (path / adapter["model"]).resolve()
        if not model.is_relative_to(path) or not model.is_file():
            raise ValueError("Component model config missing")
        adapter["model"] = str(model)
    return adapter


def validated_parameters(adapter, values):
    if not isinstance(values, dict):
        raise ValueError("Training parameters must be an object")
    declared = {x["key"]: x for x in adapter["parameters"]}
    if values.keys() - declared.keys():
        raise ValueError("Undeclared training parameters")
    result = {key: definition["default"] for key, definition in declared.items()}
    result.update(values)
    for key, value in result.items():
        rule = declared[key]
        kind = rule["type"]
        if kind in ("integer", "number"):
            if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
                raise ValueError(f"Invalid numeric parameter: {key}")
            if kind == "integer" and int(value) != value:
                raise ValueError(f"Invalid integer parameter: {key}")
            if not rule["min"] <= value <= rule["max"]:
                raise ValueError(f"Parameter out of range: {key}")
        elif kind == "boolean" and not isinstance(value, bool):
            raise ValueError(f"Invalid boolean parameter: {key}")
        elif kind == "string" and (not isinstance(value, str) or len(value) > 100):
            raise ValueError(f"Invalid string parameter: {key}")
    return result


def check_source(python, adapter):
    code = ("import sys,json;sys.path.insert(0,sys.argv[1]);import ultralytics;"
            "print(json.dumps(dict(version=ultralytics.__version__,source=ultralytics.__file__,python=sys.version)))")
    checked = subprocess.run([python, "-c", code, adapter["source_root"]], capture_output=True, text=True, timeout=45)
    if checked.returncode:
        raise ValueError("Task Python cannot load component: " + checked.stderr[-1600:])
    info = json.loads(checked.stdout.strip().splitlines()[-1])
    if info["version"] != adapter["engine_version"] or not Path(info["source"]).resolve().is_relative_to(Path(adapter["source_root"])):
        raise ValueError("Loaded algorithm source/version does not match selected component")
    return info


def build_execution(payload, work_dir, root=ROOT):
    adapter = resolve_component(payload, root)
    python = str(payload.get("training_python_path") or "")
    if not Path(python).is_file():
        raise ValueError("Task Python missing")
    data = Path(str(payload.get("ultralytics_data") or ""))
    if not data.is_absolute():
        data = root / data
    if payload.get("data_format") != "yolo" or not data.is_file():
        raise ValueError("Valid YOLO dataset required")
    # Fail before launching a training process if the YAML/root is invalid.
    from ultralytics_train_worker import resolve_dataset_yaml
    resolved = resolve_dataset_yaml(str(data), work_dir)
    import yaml
    dataset = yaml.safe_load(resolved.read_text(encoding="utf-8"))
    for split in ("train", "val"):
        sources = dataset.get(split)
        sources = sources if isinstance(sources, list) else [sources]
        if not sources or any(not isinstance(x, str) or not x.strip() for x in sources):
            raise ValueError(f"Dataset {split} sources missing")
        for source in sources:
            path = Path(source)
            if not path.is_absolute(): path = Path(dataset["path"]) / path
            if not path.exists(): raise ValueError(f"Dataset {split} source missing: {path}")

    info = check_source(python, adapter)
    spec = dict(adapter, component_commit=payload["component_commit"], adapter_digest=payload["adapter_digest"],
                platform_commit=revision(root), training_python=info,
                dataset=payload.get("dataset"), data=str(data.resolve()), work_dir=str(work_dir.resolve()),
                parameters=validated_parameters(adapter, payload.get("ultralytics_parameters", {})))
    spec["command"] = ["<task-python>", "-u", "runner/ultralytics_train_worker.py", "--spec", "<work-dir>/ultralytics_run.json"]
    spec_path = work_dir / "ultralytics_run.json"
    spec_path.write_text(json.dumps(spec, ensure_ascii=False, indent=2), encoding="utf-8")
    worker = root / "runner/ultralytics_train_worker.py"
    print(f"[component] {adapter['id']} commit={payload['component_commit']} source={info['source']}", flush=True)
    return [python, "-u", str(worker), "--spec", str(spec_path)], str(work_dir), str(worker)
