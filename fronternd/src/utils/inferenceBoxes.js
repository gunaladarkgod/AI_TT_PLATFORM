export function visibleInferenceBoxes(detections, width, height, threshold = 0) {
  if (!(width > 0 && height > 0)) return [];
  return detections.flatMap((detection, index) => {
    const score = detection?.score;
    const bbox = detection?.bbox;
    if (!Number.isFinite(score) || score < threshold || score < 0 || score > 1
      || !Array.isArray(bbox) || bbox.length !== 4 || !bbox.every(Number.isFinite)) return [];
    const [x1, y1, x2, y2] = bbox;
    const x = Math.max(0, x1);
    const y = Math.max(0, y1);
    const boxWidth = Math.min(width, x2) - x;
    const boxHeight = Math.min(height, y2) - y;
    if (boxWidth <= 0 || boxHeight <= 0) return [];
    return [{ ...detection, index, x, y, width: boxWidth, height: boxHeight }];
  });
}
