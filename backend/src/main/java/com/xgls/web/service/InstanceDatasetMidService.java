package com.xgls.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgls.web.entity.InstanceDatasetMid;

import java.util.List;

public interface InstanceDatasetMidService extends IService<InstanceDatasetMid> {
    List<InstanceDatasetMid> getAllInstanceDatasets();
}
