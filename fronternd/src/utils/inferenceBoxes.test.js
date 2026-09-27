import test from 'node:test';
import assert from 'node:assert/strict';
import { visibleInferenceBoxes } from './inferenceBoxes.js';

test('low confidence boxes remain visible by default and filter at the chosen threshold', () => {
  const detections = [0.0893, 0.024, 0.0126, 0.01].map(score => ({ label: 'bird', score, bbox: [10, 20, 30, 40] }));
  assert.equal(visibleInferenceBoxes(detections, 300, 300).length, 4);
  assert.equal(visibleInferenceBoxes(detections, 300, 300, 0.05).length, 1);
  assert.equal(visibleInferenceBoxes(detections, 300, 300, 0.1).length, 0);
  assert.equal(visibleInferenceBoxes(detections, 300, 300, 0.0893).length, 1);
});

test('xyxy boxes clip to image edges and keep their identity after filtering', () => {
  const detections = [
    { score: 0.1, bbox: [0, 0, 10, 10] },
    { score: 0.8, bbox: [-10, 20, 120, 90] },
  ];
  const [box] = visibleInferenceBoxes(detections, 100, 80, 0.5);
  assert.deepEqual([box.x, box.y, box.width, box.height, box.index], [0, 20, 100, 60, 1]);
});

test('invalid or wholly off-image boxes do not create misleading overlays', () => {
  const detections = [
    { score: NaN, bbox: [0, 0, 10, 10] },
    { score: 2, bbox: [0, 0, 10, 10] },
    { score: 0.5, bbox: [20, 20, 10, 10] },
    { score: 0.5, bbox: [200, 200, 300, 300] },
    { score: 0.5, bbox: [0, 0, Infinity, 10] },
    { score: 0.5, bbox: [0, 0, 10] },
    null,
  ];
  assert.deepEqual(visibleInferenceBoxes(detections, 100, 100), []);
  assert.deepEqual(visibleInferenceBoxes([{ score: 0.5, bbox: [0, 0, 10, 10] }], 0, 0), []);
});
