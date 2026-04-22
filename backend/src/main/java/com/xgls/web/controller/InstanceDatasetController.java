package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.dto.InstanceDatasetTrainingReadinessDto;
import com.xgls.web.service.InstanceDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 全部实例数据集的训前状态（可训练/原因），用于训练任务界面全量展示与引导至预处理、训测划分。
     */
    @PostMapping("/instancedatasets/trainingReadiness")
    public AjaxResult listInstanceDatasetTrainingReadiness() {
        try {
            List<InstanceDatasetTrainingReadinessDto> list = instanceDatasetService.listInstanceDatasetTrainingReadiness();
            return AjaxResult.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("获取实例数据集训前状态失败: " + (e.getMessage() != null ? e.getMessage() : "服务器错误"));
        }
    }

    /**
     * 按训测比（训练集图片占比 0.01~0.99）随机划分图像与成对标注到 test / train；执行前将 test 侧合并回 train 再重划。
     */
    @PostMapping("/instancedatasets/splitTrainTest")
    public AjaxResult splitInstanceDatasetTrainTest(@RequestBody Map<String, Object> body) {
        try {
            Object idObj = body != null ? body.get("id") : null;
            Object trObj = body != null ? body.get("trainRatio") : null;
            if (idObj == null || trObj == null) {
                return AjaxResult.error("缺少 id 或 trainRatio");
            }
            long id;
            if (idObj instanceof Number) {
                id = ((Number) idObj).longValue();
            } else {
                id = Long.parseLong(String.valueOf(idObj).trim());
            }
            double trainRatio;
            if (trObj instanceof Number) {
                trainRatio = ((Number) trObj).doubleValue();
            } else {
                trainRatio = Double.parseDouble(String.valueOf(trObj).trim());
            }
            var result = instanceDatasetService.splitInstanceDatasetRandomTrainTest(id, trainRatio);
            Map<String, Object> data = new HashMap<>();
            data.put("trainImages", result.trainImages());
            data.put("testImages", result.testImages());
            return AjaxResult.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error(e.getMessage() != null ? e.getMessage() : "训测划分失败");
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