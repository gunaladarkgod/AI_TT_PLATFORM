package com.xgls.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xgls.web.entity.PreprocessScriptInfo;
import com.xgls.web.service.PreprocessScriptInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/preprocess")
public class PreprocessScriptInfoController {

    @Autowired
    private PreprocessScriptInfoService preprocessScriptInfoService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取脚本列表（自动将 paramSchema 从字符串解析为 JSON 对象）
     */
    @GetMapping("/scripts")
    public List<Map<String, Object>> getScripts(@RequestParam Integer type) {
        // 1. 调用原有 Service 获取原始脚本列表
        List<PreprocessScriptInfo> scripts = preprocessScriptInfoService.getScriptsByType(type);

        // 2. 转换为 Map，并解析 paramSchema
        List<Map<String, Object>> result = new ArrayList<>();
        for (PreprocessScriptInfo script : scripts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", script.getId());
            item.put("name", script.getName());
            item.put("type", script.getType());
            // 注意：不返回 script_path 和 result_path（前端通常不需要）

            // 3. 关键：解析 paramSchema 字符串 → JSON 对象（List<Map>）
            try {
                if (script.getParamSchema() != null && !script.getParamSchema().trim().isEmpty()) {
                    List<Map<String, Object>> paramSchemaObj =
                            objectMapper.readValue(script.getParamSchema(), new TypeReference<List<Map<String, Object>>>() {});
                    item.put("paramSchema", paramSchemaObj);
                } else {
                    item.put("paramSchema", Collections.emptyList());
                }
            } catch (Exception e) {
                // 容错：解析失败时返回空数组
                item.put("paramSchema", Collections.emptyList());
            }

            result.add(item);
        }

        return result;
    }
}