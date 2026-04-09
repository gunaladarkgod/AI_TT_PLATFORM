package com.xgls.web.vo.query;


import com.xgls.web.entity.TaskDataset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "TaskDatasetQuery", description = "任务数据集查询类")
public class TaskDatasetQuery extends TaskDataset {

    @Schema(description = "分页大小")
    private Long size;
    @Schema(description = "当前页号")
    private Long current;
}
