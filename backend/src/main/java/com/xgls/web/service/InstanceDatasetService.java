package com.xgls.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgls.web.entity.InstanceDataset;

import java.util.List;

public interface InstanceDatasetService extends IService<InstanceDataset> {
    List<InstanceDataset> getAllInstanceDatasets();

    List<String> listMmdetTrainableDatasetNames();

    boolean deleteInstanceDatasetById(Long id);
}