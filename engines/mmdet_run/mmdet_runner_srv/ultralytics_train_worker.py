import argparse
import json
from pathlib import Path


def resolve_dataset_yaml(source: str, work_dir: Path) -> Path:
    """Anchor dataset paths to the original YAML, regardless of the worker's CWD."""
    import yaml

    source_path = Path(source).expanduser().resolve(strict=True)
    data = yaml.safe_load(source_path.read_text(encoding="utf-8-sig"))
    if not isinstance(data, dict):
        raise ValueError(f"Dataset YAML must contain a mapping: {source_path}")
    root_value = data.get("path")
    if root_value is not None and not isinstance(root_value, str):
        raise ValueError(f"Dataset YAML path must be a string: {source_path}")
    root = Path(root_value).expanduser() if root_value else source_path.parent
    if not root.is_absolute():
        root = source_path.parent / root
    root = root.resolve()
    if not root.is_dir():
        raise FileNotFoundError(f"Dataset root does not exist: {root} (from {source_path})")
    data["path"] = root.as_posix()
    work_dir.mkdir(parents=True, exist_ok=True)
    resolved = work_dir / "data_resolved.yaml"
    if resolved.resolve() == source_path:
        raise ValueError("Resolved dataset YAML must not overwrite the source YAML")
    resolved.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False), encoding="utf-8")
    print(f"[dataset] source={source_path}; root={root}; resolved={resolved}", flush=True)
    return resolved


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", required=True)
    args = parser.parse_args()
    spec = json.loads(Path(args.spec).read_text(encoding="utf-8"))
    from ultralytics import YOLO

    work_dir = Path(spec["work_dir"]).resolve()
    work_dir.parent.mkdir(parents=True, exist_ok=True)
    data_yaml = resolve_dataset_yaml(spec["data"], work_dir)
    model = YOLO(spec["model"])
    model.train(data=str(data_yaml), project=str(work_dir.parent), name=work_dir.name,
                exist_ok=True, **spec.get("parameters", {}))
    weights_dir = work_dir / "weights"
    if not (weights_dir / "best.pt").is_file() and not (weights_dir / "last.pt").is_file():
        raise RuntimeError(f"Ultralytics training completed without a checkpoint in {weights_dir}")


if __name__ == "__main__":
    main()
