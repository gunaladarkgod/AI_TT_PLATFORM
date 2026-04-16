package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.mapper.InstanceDatasetMidMapper;
import com.xgls.web.service.InstanceDatasetMidService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InstanceDatasetMidServiceImpl extends ServiceImpl<InstanceDatasetMidMapper, InstanceDatasetMid>
        implements InstanceDatasetMidService {

    @Autowired
    private InstanceDatasetMidMapper instanceDatasetMidMapper;

    @Override
    public List<InstanceDatasetMid> getAllInstanceDatasets() {
        return instanceDatasetMidMapper.selectList(null);
    }
}
