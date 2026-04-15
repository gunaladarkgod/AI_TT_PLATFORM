// com.xgls.web.controller.InstanceDatasetinfoController.java
package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.InstanceDatasetinfo;
import com.xgls.web.service.InstanceDatasetinfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// 获取实例数据集
@RestController
@RequestMapping("/instance")
public class InstanceDatasetinfoController {

    @Autowired
    private InstanceDatasetinfoService instanceDatasetinfoService;

    @PostMapping("/instancedatasets")
    public AjaxResult getAllInstanceDatasets() {
        try {
            List<InstanceDatasetinfo> datasetList = instanceDatasetinfoService.getAllInstanceDatasets();
            System.out.println(AjaxResult.success(datasetList));
            return AjaxResult.success(datasetList);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，获取实例数据集列表失败。");
        }
    }

    @PostMapping("/getNames")
    public AjaxResult getNames() {
        try {
            List<InstanceDatasetinfo> datasetList = instanceDatasetinfoService.getAllInstanceDatasets();
            List<String> names = new ArrayList<>();
            for (InstanceDatasetinfo dataset : datasetList) {
                names.add(dataset.getName());
            }
            System.out.println(names);

            return AjaxResult.success(names);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，获取实例数据集列表失败。");
        }
    }

    /**
     * 创建 MMDet 等训练任务时下拉框使用：仅返回磁盘可用、含有效类别配置的实例数据集名称。
     */
    @PostMapping("/getTrainableNames")
    public AjaxResult getTrainableNames() {
        try {
            List<String> names = instanceDatasetinfoService.listMmdetTrainableDatasetNames();
            return AjaxResult.success(names);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，获取可用实例数据集列表失败。");
        }
    }

    @DeleteMapping("/instancedatasets/{id}")
    public AjaxResult deleteInstanceDataset(@PathVariable Long id) {
        System.out.println(">>> 收到删除请求，id=" + id);
        try {
            if (id == null || id <= 0) {
                return AjaxResult.error("无效的实例数据集ID");
            }

            boolean success = instanceDatasetinfoService.deleteInstanceDatasetById(id);
            if (success) {
                return AjaxResult.success("实例数据集删除成功");
            } else {
                return AjaxResult.error("实例数据集不存在，删除失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("服务器内部错误，删除实例数据集失败：" + e.getMessage());
        }
    }
}