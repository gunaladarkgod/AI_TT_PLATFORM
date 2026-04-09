package com.xgls.web.vo.dataset;

import java.util.List;

import com.xgls.web.entity.TaskDataset;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Schema(description = "任务数据集子集信息")
public class TaskDatasetSubsetsInfoVO {

    @Schema(description = "任务数据集概况")
    private TaskDataset taskDataset;

    @Schema(description = "核心子集列表")
    private List<TaskDatasetSubsetItemVO> coreSubsets;

    @Schema(description = "辅助子集列表")
    private List<TaskDatasetSubsetItemVO> auxiliarySubsets;
}