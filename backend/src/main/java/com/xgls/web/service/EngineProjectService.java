package com.xgls.web.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.EngineLabel;
import com.xgls.web.entity.EngineProject;
import com.xgls.web.entity.EngineTask;
import com.xgls.web.mapper.EngineLabelMapper;
import com.xgls.web.mapper.EngineProjectMapper;
import com.xgls.web.mapper.EngineTaskMapper;

@Service
public class EngineProjectService extends ServiceImpl<EngineProjectMapper, EngineProject> {
    @Autowired
    EngineTaskMapper engineTaskMapper;
    @Autowired
    EngineLabelMapper engineLabelMapper;

    @Transactional
    public boolean removeByIdLink(Integer id) {
        if (removeById(id)) {
            // 移除所有的task
            LambdaUpdateWrapper<EngineTask> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(EngineTask::getProject_id, id);
            engineTaskMapper.delete(wrapper);
            // 移除所有的label
            LambdaUpdateWrapper<EngineLabel> wrapper2 = new LambdaUpdateWrapper<>();
            wrapper2.eq(EngineLabel::getProject_id, id);
            engineLabelMapper.delete(wrapper2);
            return true;
        }
        return false;
    }

    public List<EngineProject> getDistinctFields(String field, Integer project_id) {
        QueryWrapper<EngineProject> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT " + field).isNotNull(field).ne(field, "");
        if (project_id != null) {
            wrapper.eq("id", project_id);
        }
        return baseMapper.selectList(wrapper);
    }

}
