package com.xgls.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgls.web.entity.TaskDataset;

import java.util.List;


public interface TaskDataset1Service extends IService<TaskDataset> {
    void processTestPlan(TaskDataset task, List<String> testPlan, List<String> trainOriginalIds, int index);
}
