package com.xgls.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgls.web.dto.InstanceDatasetTrainingReadinessDto;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.utils.InstanceDatasetTrainTestRandomSplitUtil;

import java.util.List;

public interface InstanceDatasetService extends IService<InstanceDataset> {
    List<InstanceDataset> getAllInstanceDatasets();

    List<InstanceDatasetTrainingReadinessDto> listInstanceDatasetTrainingReadiness();

    List<String> listMmdetTrainableDatasetNames();

    boolean deleteInstanceDatasetById(Long id);

    /**
     * 按训测比将图像与成对标注从 train 随机拆分到 test；执行前会先把 test 侧合并回 train。
     */
    InstanceDatasetTrainTestRandomSplitUtil.SplitResult splitInstanceDatasetRandomTrainTest(Long id, double trainRatio)
            throws java.io.IOException;
}