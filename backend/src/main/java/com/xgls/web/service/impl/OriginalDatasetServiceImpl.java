package com.xgls.web.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.mapper.OriginalDatasetMapper;
import com.xgls.web.service.OriginalDataset1Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OriginalDatasetServiceImpl implements OriginalDataset1Service {

    @Autowired
    private OriginalDatasetMapper OriginalDatasetMapper;

    @Override
    public List<OriginalDataset> getAllOriginalDatasets() {
        // 查询所有原始数据集
        return OriginalDatasetMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public List<OriginalDataset> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // 使用 MyBatis-Plus 的 selectBatchIds 方法批量查询
        return OriginalDatasetMapper.selectBatchIds(ids);
    }
}

