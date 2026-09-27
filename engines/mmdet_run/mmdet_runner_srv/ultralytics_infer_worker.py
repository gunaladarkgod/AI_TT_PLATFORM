import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--checkpoint', required=True)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--device', default=None)
    args = parser.parse_args()

    from ultralytics import YOLO

    output_dir = Path(args.output_dir).resolve()
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    model = YOLO(args.checkpoint)
    results = model.predict(
        source=args.input,
        save=True,
        project=str(output_dir.parent),
        name=output_dir.name,
        exist_ok=True,
        verbose=False,
        **({'device': args.device} if args.device else {}),
    )
    if not results:
        raise RuntimeError('Ultralytics did not return an inference result')
    result = results[0]
    names = result.names or {}
    detections = []
    if result.boxes is not None:
        for box in result.boxes:
            class_id = int(box.cls.item())
            detections.append({
                'label': str(names.get(class_id, class_id)),
                'score': round(float(box.conf.item()), 4),
                'bbox': [round(float(value), 2) for value in box.xyxy[0].tolist()],
            })
    images = sorted(
        (path for path in output_dir.rglob('*') if path.suffix.lower() in {'.jpg', '.jpeg', '.png', '.bmp'}),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    if not images:
        raise RuntimeError('Ultralytics did not generate a visualization image')
    (output_dir / 'result.json').write_text(json.dumps({
        'output_path': str(images[0]),
        'detections': detections,
    }, ensure_ascii=False), encoding='utf-8')


if __name__ == '__main__':
    main()
