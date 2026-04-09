package com.xgls.web.vo.dataset;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "合并目标子集请求体")
public class TaskDatasetMergeTargetRequestVO {

    @Schema(description = "父级数据集ID")
    private Long parentId;

    @Schema(description = "目标任务数据集名称")
    private String taskDatasetName;

    @Schema(description = "选择的核心子集ID列表")
    private List<Long> selectedCoreSubsetIds;

    @Schema(description = "标签映射列表")
    private List<TaskDatasetLabelMappingVO> labelMapping;

    @Schema(description = "传感器类型")
    private String sensorType;

    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "模板ID")
    private Long templateId;
}