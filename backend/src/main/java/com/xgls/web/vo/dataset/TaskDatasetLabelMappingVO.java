package com.xgls.web.vo.dataset;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签映射关系")
public class TaskDatasetLabelMappingVO {

    @Schema(description = "源标签")
    private String sourceLabel;

    @Schema(description = "目标标签")
    private String targetLabel;
}