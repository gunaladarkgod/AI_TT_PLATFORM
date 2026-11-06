"""Resolve the one administrator-installed external baseline."""
import json
import subprocess
from pathlib import Path

import yaml

from ultralytics_train_worker import resolve_dataset_yaml


PILOT_ID = "small-object/yolo11-baseline"
EPOCHS_PARAMETER = {"key": "epochs", "type": "integer", "min": 1, "max": 1000}


def read_yaml(path):
    value = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected a YAML mapping in {path.name}")
    return value


def component_file(root, relative):
    candidate = Path(relative)
    if candidate.is_absolute() or candidate.anchor or ".." in candidate.parts:
        raise ValueError(f"Unsafe algorithm path: {relative}")
    resolved = (root / candidate).resolve()
    if not resolved.is_relative_to(root) or not resolved.is_file():
        raise ValueError(f"Missing or unsafe algorithm file: {relative}")
    return resolved


def revision(root):
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--show-toplevel", "HEAD"],
        capture_output=True, text=True, timeout=10, check=True,
    )
    top, commit = result.stdout.splitlines()
    if Path(top).resolve() != root:
        raise ValueError("Expected an independent Git checkout")
    return commit


def prepare_pilot(workspace, algorithm_id, python, data, output, parameters, dataset_id):
    if algorithm_id != PILOT_ID:
        raise ValueError("Unknown external algorithm ID")
    if not isinstance(parameters, dict) or parameters.keys() - {"epochs"}:
        raise ValueError("The only supported override is epochs")
    if "epochs" in parameters and (type(parameters["epochs"]) is not int or not 1 <= parameters["epochs"] <= 1000):
        raise ValueError("epochs must be an integer from 1 to 1000")

    root = workspace / "components" / "small-object" / "yolo11-baseline"
    if not root.is_dir():
        raise ValueError("External algorithm source is not installed")
    if root.resolve() != root:
        raise ValueError("External algorithm checkout must be inside the fixed component directory")
    requirement = workspace / "engines/yolo_run/ultralytics_runner/requirements.txt"
    if not requirement.is_file():
        raise ValueError("Ultralytics engine requirements file is missing")

    record = read_yaml(workspace / "research-registry/algorithms/small-object-yolo11-baseline.yaml")
    source = record["source"]
    if source["type"] != "external-git":
        raise ValueError("Pilot registration source.type must be external-git")
    manifest = read_yaml(component_file(root, source["manifest"]))
    expected = {
        "schema_version": 1, "id": PILOT_ID, "kind": "baseline",
        "engine": "ultralytics", "engine_version": "8.4.115",
        "data_format": "yolo",
    }
    registered = {
        "id": record["id"], "engine": record["engine"]["id"],
        "engine_version": record["engine"]["version"], "data_format": record["data_format"],
    }
    for field, value in expected.items():
        if manifest.get(field) != value or registered.get(field, value) != value:
            raise ValueError(f"External algorithm manifest mismatch: {field}")

    declared = manifest.get("parameters")
    if declared not in ([], [EPOCHS_PARAMETER]):
        raise ValueError("External algorithm manifest mismatch: parameters")
    if parameters and not declared:
        raise ValueError("Installed algorithm revision does not support epochs overrides")

    entry = component_file(root, manifest["entry"])
    config = read_yaml(component_file(root, manifest["default_config"]))
    if not data.resolve().is_relative_to(workspace):
        raise ValueError("Pilot data YAML must be inside the workspace")
    data_yaml = resolve_dataset_yaml(str(data), output, validate_splits=True)
    spec_path = output / "external_algorithm_execution.json"
    spec_path.write_text(json.dumps({
        "data": str(data_yaml), "work_dir": str(output), "parameters": parameters,
    }), encoding="utf-8")
    spec = {
        "platform_commit": revision(workspace),
        "algorithm_commit": revision(root),
        "algorithm_id": algorithm_id,
        "algorithm_repository": source["repository"],
        "manifest": manifest,
        "config": config,
        "engine": expected["engine"],
        "engine_version": expected["engine_version"],
        "data_format": expected["data_format"],
        "dataset_id": dataset_id,
        "data": data.resolve().relative_to(workspace).as_posix(),
        "work_dir": output.resolve().relative_to(workspace).as_posix(),
        "model": config["model"],
        "parameters": config["parameters"] | parameters,
        "command": ["<task-python>", "-u", entry.relative_to(workspace).as_posix(),
                    "--spec", spec_path.relative_to(workspace).as_posix()],
    }
    (output / "ultralytics_run.json").write_text(
        json.dumps(spec, ensure_ascii=False, indent=2), encoding="utf-8",
    )
    return [str(python), "-u", str(entry), "--spec", str(spec_path)], str(output), str(entry)
