package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.service.InstanceDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/instance")
public class InstanceDatasetController {

    @Autowired
    private InstanceDatasetService instanceDatasetService;

    @PostMapping("/instancedatasets")
    public AjaxResult getAllInstanceDatasets() {
        try {
            List<InstanceDataset> list = instanceDatasetService.getAllInstanceDatasets();
            return AjaxResult.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，获取实例数据集列表失败。");
        }
    }

    @PostMapping("/getNames")
    public AjaxResult getNames() {
        try {
            List<InstanceDataset> datasetList = instanceDatasetService.getAllInstanceDatasets();
            List<String> names = new ArrayList<>();
            for (InstanceDataset dataset : datasetList) {
                names.add(dataset.getName());
            }
            return AjaxResult.success(names);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，获取实例数据集列表失败。");
        }
    }

    @PostMapping("/getTrainableNames")
    public AjaxResult getTrainableNames() {
        try {
            List<String> names = instanceDatasetService.listMmdetTrainableDatasetNames();
            return AjaxResult.success(names);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，获取可用实例数据集列表失败。");
        }
    }

    @DeleteMapping("/instancedatasets/{id}")
    public AjaxResult deleteInstanceDataset(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return AjaxResult.error("无效的实例数据集ID");
            }

            boolean success = instanceDatasetService.deleteInstanceDatasetById(id);
            if (success) {
                return AjaxResult.success("实例数据集删除成功");
            }
            return AjaxResult.error("实例数据集不存在，删除失败");
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，删除实例数据集失败：" + e.getMessage());
        }
    }
}