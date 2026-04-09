package com.xgls.web.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.UserProject;
import com.xgls.web.mapper.UserProjectMapper;
import com.xgls.web.service.UserProjectService;

@Service
public class UserProjectService extends ServiceImpl<UserProjectMapper, UserProject> {
    public List<Integer> getProjects(Long user_id) {
        LambdaQueryWrapper<UserProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserProject::getUser_id, user_id);
        wrapper.select(UserProject::getProject_id);
        return baseMapper.selectList(wrapper).stream().map(item -> item.getProject_id()).toList();
    }
}
