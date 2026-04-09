package com.xgls.web.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.EngineLabel;
import com.xgls.web.entity.TrainLabel;
import com.xgls.web.mapper.EngineLabelMapper;

import cn.hutool.core.util.StrUtil;

@Service
public class EngineLabelService extends ServiceImpl<EngineLabelMapper, EngineLabel> {
    public List<Integer> getProjectByLabel(String name, Integer project_id) {
        if (StrUtil.isBlank(name)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<EngineLabel> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(EngineLabel::getProject_id);
        if (project_id != null) {
            wrapper.eq(EngineLabel::getProject_id, project_id);
        }
        wrapper.eq(EngineLabel::getName, name);
        List<EngineLabel> list = list(wrapper);
        return list.stream().map(item -> item.getProject_id()).toList();
    }

    public List<String> queryDistinctNames(Integer project_id) {
        return baseMapper.queryDistinctNames(project_id);
    }

    public List<TrainLabel> queryNewLabels() {
        return baseMapper.queryNewLabels();
    }

    public List<Integer> queryProjectsOrLabels(List<String> labels) {
        return baseMapper.queryProjectsByOrLabels(labels);
    }

    public List<Map<String, Object>> queryProjectLabels(String a_l, String a_s, String a_g, Long user_id) {
        return baseMapper.queryProjectLabels(a_l, a_s, a_g, user_id);
    }
}
