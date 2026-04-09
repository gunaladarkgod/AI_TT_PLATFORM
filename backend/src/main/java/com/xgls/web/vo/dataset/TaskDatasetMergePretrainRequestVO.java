package com.xgls.web.vo.dataset;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "合并预训练子集请求体")
public class TaskDatasetMergePretrainRequestVO {

    @Schema(description = "目标任务数据集ID")
    private Long taskDatasetId;

    @Schema(description = "选中的辅助子集ID")
    private List<Long> selectedAuxiliarySubsetIds;

    @Schema(description = "标签映射列表")
    private List<TaskDatasetLabelMappingVO> labelMapping;

    @Schema(description = "用户名")
    private String username;
}