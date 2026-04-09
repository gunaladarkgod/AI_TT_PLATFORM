package com.xgls.web.controller;

import com.xgls.web.common.Result;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.service.InstanceDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/instance")
public class InstanceDatasetController {

    @Autowired
    private InstanceDatasetService instanceDatasetService;

    /**
     * 获取所有实例数据集（作为预处理的源数据集）
     */
    @GetMapping("/sourcedatasets")
    public Result<List<InstanceDataset>> getSourceInstanceDatasets() {
        try {
            List<InstanceDataset> list = instanceDatasetService.getAllInstanceDatasets();
            return Result.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取源实例数据集列表失败");
        }
    }
}