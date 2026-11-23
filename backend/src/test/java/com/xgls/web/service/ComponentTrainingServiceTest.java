package com.xgls.web.service;
import cn.hutool.json.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComponentTrainingServiceTest {
    @TempDir Path root;
    @Test void downloadedComponentsOnlyAndParametersValidated() throws Exception {
        Files.createDirectories(root.resolve("research-registry"));
        Files.writeString(root.resolve("research-registry/training-adapters.json"), """
            [{"id":"small-object/yolo11","kind":"baseline","direction":"small-object","model":"yolo11s.pt",
              "parameters":[{"key":"epochs","type":"integer","default":100,"min":1,"max":1000}]}]
            """);
        AlgorithmComponentService components = mock(AlgorithmComponentService.class);
        when(components.list(anyString(), eq("small-object"))).thenReturn(Map.of("components", List.of()));
        ComponentTrainingService service = new ComponentTrainingService(components, root);
        assertTrue(service.list("small-object").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.require("small-object/yolo11", "small-object"));
        JSONObject adapter = JSONUtil.parseArray(Files.readString(root.resolve("research-registry/training-adapters.json"))).getJSONObject(0);
        JSONObject params = JSONUtil.parseObj("{\"ultralytics_parameters\":{\"epochs\":1}}");
        service.validateParameters(adapter, params);
        assertEquals(1, params.getJSONObject("ultralytics_parameters").getInt("epochs"));
        for (String invalid : List.of("{\"epochs\":0}", "{\"epochs\":true}", "{\"project\":\"escape\"}")) {
            JSONObject bad = new JSONObject().set("ultralytics_parameters", JSONUtil.parseObj(invalid));
            assertThrows(IllegalArgumentException.class, () -> service.validateParameters(adapter, bad));
        }
    }
    @Test void componentOptionsFollowDownloadRemoveAndCheckModel() throws Exception {
        Path directory = root.resolve("local-components/algorithms/small-object/tdsc");
        Files.createDirectories(directory.resolve("ultralytics"));
        Files.writeString(directory.resolve("ultralytics/__init__.py"), "version");
        for (String[] command : List.of(new String[]{"git","init"}, new String[]{"git","add","."},
                new String[]{"git","-c","user.name=Test","-c","user.email=test@example.invalid","commit","-m","fixture"})) {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes(); assertEquals(0, process.waitFor());
        }
        Files.createDirectories(root.resolve("research-registry"));
        Files.writeString(root.resolve("research-registry/training-adapters.json"), """
            [{"id":"small-object/tdsc","kind":"algorithm","direction":"small-object","model":"model.yaml","parameters":[]}]
            """);
        AlgorithmComponentService components = mock(AlgorithmComponentService.class);
        when(components.list("baseline", "small-object")).thenReturn(Map.of("components", List.of()));
        Map<String,Object> row = new HashMap<>(Map.of("id","small-object/tdsc", "kind","algorithm", "path","local-components/algorithms/small-object/tdsc", "localStatus","not-downloaded"));
        when(components.list("algorithm", "small-object")).thenAnswer(call -> Map.of("components",List.of(row)));
        ComponentTrainingService service = new ComponentTrainingService(components, root);
        assertTrue(service.list("small-object").isEmpty());
        row.put("localStatus", "downloaded");
        assertFalse(service.list("small-object").get(0).getBool("valid"));
        Files.writeString(directory.resolve("model.yaml"), "model");
        assertTrue(service.require("small-object/tdsc", "small-object").getBool("valid"));
        Files.writeString(directory.resolve("ultralytics/__init__.py"), "changed");
        assertThrows(IllegalArgumentException.class, () -> service.require("small-object/tdsc", "small-object"));
        row.put("localStatus", "not-downloaded");
        assertTrue(service.list("small-object").isEmpty());
    }

    @Test void queuedTaskPreventsComponentRemoval() throws Exception {
        ResearchRegistryService registry = mock(ResearchRegistryService.class);
        when(registry.load()).thenReturn(Map.of("valid",true,"registry",Map.of("baselines", List.of(Map.of("id","small-object/yolo11")))));
        TrainTaskService tasks = mock(TrainTaskService.class);
        com.xgls.web.entity.TrainTask task = new com.xgls.web.entity.TrainTask();
        task.setId(1); task.setStatus(2); task.setName("queued");
        when(tasks.list()).thenReturn(List.of(task));
        when(tasks.runnerOptionsForTask(1)).thenReturn(new JSONObject().set("component_id","small-object/yolo11").set("component_kind","baseline"));
        AlgorithmComponentService service = new AlgorithmComponentService(root, registry);
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(service,"trainingTasks",tasks);
            assertThrows(IllegalArgumentException.class, () -> service.remove("baseline","small-object/yolo11"));
        } finally { service.close(); }
    }

}
