import argparse
import json
from pathlib import Path


def _detections(prediction, classes):
    labels = prediction.get('labels', [])
    scores = prediction.get('scores', [])
    boxes = prediction.get('bboxes', [])
    out = []
    for label, score, box in zip(labels, scores, boxes):
        class_id = int(label)
        out.append({
            'label': classes[class_id] if 0 <= class_id < len(classes) else str(class_id),
            'score': round(float(score), 4),
            'bbox': [round(float(value), 2) for value in box],
        })
    return out


def _find_visualization_image(output_dir):
    """MMDetection 3.0 writes visualizations to ``vis``; keep old output compatible."""
    images = []
    for directory in (output_dir / 'vis', output_dir / 'visualizations'):
        if not directory.exists():
            continue
        images.extend(
            path for path in directory.rglob('*')
            if path.suffix.lower() in {'.jpg', '.jpeg', '.png', '.bmp'}
        )
    return max(images, key=lambda path: path.stat().st_mtime, default=None)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', required=True)
    parser.add_argument('--checkpoint', required=True)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--device', default=None)
    args = parser.parse_args()

    from mmdet.apis import DetInferencer

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    init_args = {'model': args.config, 'weights': args.checkpoint}
    if args.device:
        init_args['device'] = args.device
    inferencer = DetInferencer(**init_args)
    result = inferencer(args.input, out_dir=str(output_dir), no_save_pred=True)
    predictions = result.get('predictions') or []
    prediction = predictions[0] if predictions else {}
    classes = list((inferencer.visualizer.dataset_meta or {}).get('classes') or [])
    image = _find_visualization_image(output_dir)
    if image is None:
        raise RuntimeError('MMDetection did not generate a visualization image')
    (output_dir / 'result.json').write_text(json.dumps({
        'output_path': str(image),
        'detections': _detections(prediction, classes),
    }, ensure_ascii=False), encoding='utf-8')


if __name__ == '__main__':
    main()
