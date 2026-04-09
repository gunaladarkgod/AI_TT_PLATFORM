package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.mapper.InstanceDatasetMapper;
import com.xgls.web.service.InstanceDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InstanceDatasetServiceImpl extends ServiceImpl<InstanceDatasetMapper, InstanceDataset> implements InstanceDatasetService {

    @Autowired
    private InstanceDatasetMapper instanceDatasetMapper;

    @Override
    public List<InstanceDataset> getAllInstanceDatasets() {
        return instanceDatasetMapper.selectList(null); // MyBatis-Plus 查询所有
    }
}