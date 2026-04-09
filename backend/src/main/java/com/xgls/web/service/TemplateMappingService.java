package com.xgls.web.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.TemplateMapping;
import com.xgls.web.mapper.TemplateMappingMapper;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TemplateMappingService extends ServiceImpl<TemplateMappingMapper, TemplateMapping> {

    public TemplateMapping uploadTemplate(String name, List<String> classList) {
        TemplateMapping existing = lambdaQuery()
                .eq(TemplateMapping::getName, name)
                .one();
        if (existing != null) {
            throw new IllegalArgumentException("模板名称已存在");
        }

        TemplateMapping template = new TemplateMapping();
        template.setName(name);
        template.setClassList(JSONUtil.toJsonStr(classList));
        template.setCreatedTime(LocalDateTime.now());
        save(template);
        return template;
    }

    public List<TemplateMapping> getAllTemplates() {
        return lambdaQuery()
                .orderByDesc(TemplateMapping::getCreatedTime)
                .list();
    }
}