package com.xgls.web.controller;

import com.xgls.web.common.Result;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.service.InstanceDatasetMidService;
import com.xgls.web.utils.InstanceDatasetPathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/instance-mid")
public class InstanceDatasetMidController {

    @Autowired
    private InstanceDatasetMidService instanceDatasetMidService;

    /** 与任务数据集导出根目录一致，用于库中绝对路径失效时按 name/father_name 回退解析 */
    @Value("${sys.instancecfg.instancedata-mid-root:/home/omen1/AI_TT_Platform/data/instance_dataset_mid/}")
    private String instanceDatasetMidRoot;

    /**
     * 获取所有中间实例数据集（作为预处理的源数据集）。
     * 数据来自表 {@code instance_dataset_mid}（若库中无该表则回退读 {@code instance_dataset}，与导出写入逻辑一致）。
     *
     * @param presentOnDisk 为 true 时仅返回四类路径均在磁盘上存在、且训练目录（含子目录）含图像与标注的源数据集
     */
    @GetMapping("/sourcedatasets")
    public Result<List<InstanceDatasetMid>> getSourceInstanceDatasets(
            @RequestParam(value = "presentOnDisk", required = false, defaultValue = "false") boolean presentOnDisk) {
        try {
            List<InstanceDatasetMid> list = instanceDatasetMidService.getAllInstanceDatasets();
            if (presentOnDisk) {
                String midRoot = instanceDatasetMidRoot;
                list = list.stream()
                        .filter(mid -> InstanceDatasetPathUtil.isSourceInstanceDatasetOnDisk(mid, midRoot))
                        .collect(Collectors.toList());
            }
            return Result.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取中间实例数据集列表失败");
        }
    }
}
