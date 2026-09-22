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
        assertEquals(1, baselines.size());
        assertTrue(Boolean.TRUE.equals(baselines.get(0).get("valid")));
        assertEquals("small-object/mmdet-faster-rcnn-r50-v1", baselines.get(0).get("id"));

        List<Map<String, Object>> improvements = service.listImprovements("small-object/mmdet-faster-rcnn-r50-v1");
        assertEquals(1, improvements.size());
        assertTrue(Boolean.TRUE.equals(improvements.get(0).get("valid")));
        assertEquals("small-object/mmdet-faster-rcnn-r50-v1/feature-fusion-v1", improvements.get(0).get("id"));

        Map<String, Object> detail = service.detail("knowledge-distillation/mmdet-detr-r50-v1/decoupled-kd-v1");
        assertNotNull(detail);
        assertEquals("improvement", detail.get("kind"));
        assertFalse(((List<?>) detail.get("parameters")).isEmpty());
    }
}
