package com.xgls.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.TemplateMapping;
import com.xgls.web.service.TemplateMappingService;
import com.xgls.web.vo.template.TemplateUploadVO;

import cn.hutool.json.JSONUtil;

@RestController
@RequestMapping("/api/template")
public class TemplateController {

    @Autowired
    private TemplateMappingService templateMappingService;

    @PostMapping("/upload")
    public AjaxResult uploadTemplate(@RequestBody TemplateUploadVO request) {
        try {
            List<String> classList = JSONUtil.toList(JSONUtil.parseArray(request.getClassListJson()), String.class);
            TemplateMapping template = templateMappingService.uploadTemplate(request.getName(), classList);
            return AjaxResult.success(template);
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            return AjaxResult.error("模板上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public AjaxResult getTemplateList() {
        List<TemplateMapping> templates = templateMappingService.getAllTemplates();
        return AjaxResult.success(templates);
    }
}