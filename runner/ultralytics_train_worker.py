import argparse
import json
from pathlib import Path


# Keep in sync with the image formats supported by the pinned Ultralytics runtime.
_IMAGE_SUFFIXES = frozenset({
    ".avif", ".bmp", ".dng", ".heic", ".heif", ".jp2", ".jpeg", ".jpg",
    ".mpo", ".png", ".tif", ".tiff", ".webp",
})


def _is_image_file(path: Path) -> bool:
    return path.suffix.lower() in _IMAGE_SUFFIXES and path.is_file()


def _validate_image_split(data: dict, key: str, root: Path, work_dir: Path) -> None:
    value = data.get(key)
    if key == "val" and key not in data:
        value = data.get("validation")
    sources = value if isinstance(value, list) else [value]
    if not sources or any(not isinstance(item, str) or not item.strip() for item in sources):
        raise ValueError(f"YOLO data.yaml '{key}' must specify an image directory or image list")

    has_images = False
    for item in sources:
        source = (root / item).resolve()
        # Ultralytics retries a missing single path without its leading ../.
        if not source.exists() and isinstance(value, str) and item.startswith("../"):
            source = (root / item[3:]).resolve()
        if source.is_dir():
            has_images |= any(_is_image_file(path) for path in source.rglob("*"))
        elif source.is_file():
            try:
                lines = source.read_text(encoding="utf-8").strip().splitlines()
            except (OSError, UnicodeError) as exc:
                raise ValueError(f"Cannot read YOLO data.yaml '{key}' image list: {source}") from exc
            # ./ entries are relative to the list. Other relative entries use the training CWD.
            has_images |= any(
                _is_image_file((source.parent if line.startswith("./") else work_dir) / line)
                for line in lines if line
            )
        else:
            raise FileNotFoundError(f"YOLO data.yaml '{key}' image source does not exist: {source}")
    if not has_images:
        raise ValueError(f"YOLO data.yaml '{key}' contains no supported image files")


def resolve_dataset_yaml(source: str, work_dir: Path, *, validate_splits: bool = False) -> Path:
    """Anchor dataset paths to the original YAML, regardless of the worker's CWD."""
    import yaml

    source_path = Path(source).expanduser().resolve(strict=True)
    try:
        data = yaml.safe_load(source_path.read_text(encoding="utf-8-sig"))
    except yaml.YAMLError as exc:
        mark = getattr(exc, "problem_mark", None)
        location = f" (line {mark.line + 1}, column {mark.column + 1})" if mark else ""
        problem = getattr(exc, "problem", None) or "invalid YAML"
        raise ValueError(f"Dataset YAML syntax error{location}: {problem} ({source_path})") from exc
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
    if validate_splits:
        for split in ("train", "val"):
            _validate_image_split(data, split, root, work_dir)
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
