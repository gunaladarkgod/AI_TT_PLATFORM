package com.xgls.web.controller;

import com.xgls.web.common.Result;
import com.xgls.web.entity.InstanceDataset;
import com.xgls.web.service.InstanceDatasetService;
import com.xgls.web.utils.InstanceDatasetPathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/instance")
public class InstanceDatasetController {

    @Autowired
    private InstanceDatasetService instanceDatasetService;

    /**
     * 获取所有实例数据集（作为预处理的源数据集）。
     *
     * @param presentOnDisk 为 true 时仅返回四类路径均在磁盘上存在、且训练目录含图像与 DOTA 标注的源数据集
     */
    @GetMapping("/sourcedatasets")
    public Result<List<InstanceDataset>> getSourceInstanceDatasets(
            @RequestParam(value = "presentOnDisk", required = false, defaultValue = "false") boolean presentOnDisk) {
        try {
            List<InstanceDataset> list = instanceDatasetService.getAllInstanceDatasets();
            if (presentOnDisk) {
                list = list.stream()
                        .filter(InstanceDatasetPathUtil::isSourceInstanceDatasetOnDisk)
                        .collect(Collectors.toList());
            }
            return Result.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取源实例数据集列表失败");
        }
    }
}