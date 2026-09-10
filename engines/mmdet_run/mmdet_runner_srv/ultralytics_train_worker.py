import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", required=True)
    args = parser.parse_args()
    spec = json.loads(Path(args.spec).read_text(encoding="utf-8"))
    from ultralytics import YOLO

    work_dir = Path(spec["work_dir"]).resolve()
    work_dir.parent.mkdir(parents=True, exist_ok=True)
    model = YOLO(spec["model"])
    model.train(data=spec["data"], project=str(work_dir.parent), name=work_dir.name,
                exist_ok=True, **spec.get("parameters", {}))


if __name__ == "__main__":
    main()
