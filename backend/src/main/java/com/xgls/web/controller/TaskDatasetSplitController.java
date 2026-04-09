package com.xgls.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.dto.TaskDatasetQueryDTO;
import com.xgls.web.dto.TrainTestSplitDTO;
import com.xgls.web.entity.*;
import com.xgls.web.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;

/**
 * 任务数据集控制器
 */
@RestController
@RequestMapping("/instance")
public class TaskDatasetSplitController {

    @Autowired
    private TaskDataset1Service taskDatasetService;

    @Autowired
    private ImgInfoService imgInfoService;


    /**
     * 获取所有任务数据集列表
     * @return 任务数据集列表
     */
    // TaskDatasetController.java
    @PostMapping("/taskdatasets")
    public AjaxResult getTaskDatasets(@RequestBody TaskDatasetQueryDTO dto) {
        IPage<TaskDataset> page = taskDatasetService.page(
                new Page<>(dto.getCurrent(), dto.getSize()),
                Wrappers.lambdaQuery(TaskDataset.class)
                        .like(StrUtil.isNotBlank(dto.getName()), TaskDataset::getName, dto.getName())
                        .eq(dto.getType() != null, TaskDataset::getDataFormat, dto.getType())
                        .eq(StrUtil.isNotBlank(dto.getCreatedBy()), TaskDataset::getUsername, dto.getCreatedBy())
                        .orderByDesc(TaskDataset::getCreatedTime)
        );
        return AjaxResult.success(page);
    }


    @GetMapping("/targetSubsets")
    public AjaxResult getTargetSubsets(@RequestParam Long taskId) {
        try {
            TaskDataset task = taskDatasetService.getById(taskId);
            if (task == null) return AjaxResult.error("任务不存在");

            // 1. 解析 core_id
            String[] coreIds = task.getCoreId().split("_");
            List<Long> originalIds = Arrays.stream(coreIds)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            // 2. 查询原始数据集
            List<OriginalDataset> originals = originalDatasetService.listByIds(originalIds);

            // 3. 拆分每个 original_dataset 的 task_name
            List<Map<String, String>> result = new ArrayList<>();
            for (OriginalDataset orig : originals) {
                String taskNameStr = orig.getTaskName(); // "train_1_train_2_test_1_common_1"
                String taskIdStr = orig.getTaskId();     // "1_2_3_4"

                if (taskNameStr == null || taskIdStr == null) continue;

                // 按下划线分割
                String[] nameTokens = taskNameStr.split("_");
                String[] idTokens = taskIdStr.split("_");

                boolean isOneToOne = nameTokens.length == idTokens.length;

                for (int i = 0; i < idTokens.length; i++) {
                    String fullName;
                    String purpose = "both"; // 默认为 both

                    if (isOneToOne) {
                        // --- 逻辑分支 A：你的数据格式 (一对一) ---
                        fullName = nameTokens[i]; // 直接取第 i 个名字，不乘2

                        // 尝试从名字中解析用途 (train/test/common)
                        if (fullName.contains("train")) {
                            purpose = "train";
                        } else if (fullName.contains("test")) {
                            purpose = "test";
                        } else if (fullName.contains("common")) {
                            purpose = "common";
                        }
                    } else {
                        // --- 逻辑分支 B：老代码逻辑 (一对二，前缀_后缀) ---
                        // 必须先检查越界，防止崩溃
                        if ((i * 2 + 1) >= nameTokens.length) {
                            break; // 数据不对劲，直接跳出，保护程序不崩
                        }
                        String prefix = nameTokens[i * 2];
                        String nameSuffix = nameTokens[i * 2 + 1];
                        fullName = prefix + "_" + nameSuffix;

                        if ("train".equals(prefix)) purpose = "train";
                        else if ("test".equals(prefix)) purpose = "test";
                        else if ("common".equals(prefix)) purpose = "common";
                    }

                    String realTaskId = idTokens[i];
                    String uniqueId = orig.getId() + "_" + realTaskId;

                    result.add(Map.of(
                            "id", uniqueId,
                            "name", fullName,
                            "purpose", purpose
                    ));
                }
            }

            return AjaxResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("加载目标子集失败: " + e.getMessage());
        }
    }

    @Autowired
    private OriginalDataset1Service originalDatasetService;

    @GetMapping("/pretrainSubsets")
    public AjaxResult getPretrainSubsets(@RequestParam Long taskId) {
        try {
            TaskDataset task = taskDatasetService.getById(taskId);
            if (task == null) {
                return AjaxResult.error("任务不存在");
            }

            String[] names = task.getSupName() != null ? task.getSupName().split("_") : new String[0];
            String[] ids = task.getSupId() != null ? task.getSupId().split("_") : new String[0];
            int len = Math.min(names.length, ids.length);

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                result.add(Map.of(
                        "id", ids[i].trim(),
                        "name", names[i].trim(),
                        "imgCount", task.getSupImgNum() // 共享总数
                ));
            }
            return AjaxResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("加载预训练子集失败");
        }
    }

    @PostMapping("/trainTestSplit/save")
    public AjaxResult saveTrainTestSplit(@RequestBody TrainTestSplitDTO dto) {
        try {
            TaskDataset task = taskDatasetService.getById(dto.getTaskId());
            if (task == null) {
                return AjaxResult.error("任务不存在");
            }

            // 处理每个 testPlan
            for (int i = 0; i < dto.getTestPlans().size(); i++) {
                taskDatasetService.processTestPlan(task, dto.getTestPlans().get(i), dto.getTrainOriginalIds(), i + 1);
            }

            return AjaxResult.success("成功生成 " + dto.getTestPlans().size() + " 个实例数据集");
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("保存失败: " + e.getMessage());
        }
    }

}