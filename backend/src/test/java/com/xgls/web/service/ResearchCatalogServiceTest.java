package com.xgls.web.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchCatalogServiceTest {

    private final ResearchCatalogService service = new ResearchCatalogService();

    @Test
    void scansDraftDirectionsBaselinesAndImprovementsFromProjectFiles() {
        List<Map<String, Object>> directions = service.listDirections();
        assertEquals(2, directions.size());
        assertTrue(directions.stream().anyMatch(item -> "small-object".equals(item.get("id"))));
        assertTrue(directions.stream().anyMatch(item -> "knowledge-distillation".equals(item.get("id"))));

        List<Map<String, Object>> baselines = service.listBaselines("small-object");
        assertEquals(3, baselines.stream().filter(item -> "ultralytics".equals(item.get("engine"))).count());
        Map<String, Map<String, Object>> ultralyticsBaselines = baselines.stream()
                .filter(item -> "ultralytics".equals(item.get("engine")))
                .collect(java.util.stream.Collectors.toMap(item -> String.valueOf(item.get("id")), item -> item));
        assertEquals("yolov5su.pt", ultralyticsBaselines.get("small-object/ultralytics-yolov5s-v1").get("model"));
        assertEquals("yolov5mu.pt", ultralyticsBaselines.get("small-object/ultralytics-yolov5m-v1").get("model"));
        assertEquals("yolo11n.pt", ultralyticsBaselines.get("small-object/ultralytics-yolo11n-v1").get("model"));
        for (Map<String, Object> baseline : baselines) {
            if ("ultralytics".equals(baseline.get("engine"))) {
                assertTrue(Boolean.TRUE.equals(baseline.get("valid")));
                assertEquals(14, ((List<?>) baseline.get("parameters")).size());
            }
        }
        Map<String, Object> mmdetBaseline = baselines.stream()
                .filter(item -> "mmdet".equals(item.get("engine"))).findFirst().orElseThrow();
        assertTrue(Boolean.TRUE.equals(mmdetBaseline.get("valid")));
        assertEquals("small-object/mmdet-faster-rcnn-r50-v1", mmdetBaseline.get("id"));

        List<Map<String, Object>> improvements = service.listImprovements("small-object/mmdet-faster-rcnn-r50-v1");
        assertEquals(1, improvements.size());
        assertTrue(Boolean.TRUE.equals(improvements.get(0).get("valid")));
        assertEquals("small-object/mmdet-faster-rcnn-r50-v1/feature-fusion-v1", improvements.get(0).get("id"));

        Map<String, Object> detail = service.detail("knowledge-distillation/mmdet-detr-r50-v1/decoupled-kd-v1");
        assertNotNull(detail);
        assertEquals("improvement", detail.get("kind"));
        assertFalse(((List<?>) detail.get("parameters")).isEmpty());

        Map<String, Map<String, Object>> distillationUltralyticsBaselines = service.listBaselines("knowledge-distillation").stream()
                .filter(item -> "ultralytics".equals(item.get("engine")))
                .collect(java.util.stream.Collectors.toMap(item -> String.valueOf(item.get("id")), item -> item));
        assertEquals(3, distillationUltralyticsBaselines.size());
        assertEquals("yolov5su.pt", distillationUltralyticsBaselines
                .get("knowledge-distillation/ultralytics-yolov5s-v1").get("model"));
        assertEquals("yolov5mu.pt", distillationUltralyticsBaselines
                .get("knowledge-distillation/ultralytics-yolov5m-v1").get("model"));
        assertEquals("yolo11n.pt", distillationUltralyticsBaselines
                .get("knowledge-distillation/ultralytics-yolo11n-v1").get("model"));
        for (Map<String, Object> baseline : distillationUltralyticsBaselines.values()) {
            assertTrue(Boolean.TRUE.equals(baseline.get("valid")));
            assertEquals(14, ((List<?>) baseline.get("parameters")).size());
        }
    }
}
