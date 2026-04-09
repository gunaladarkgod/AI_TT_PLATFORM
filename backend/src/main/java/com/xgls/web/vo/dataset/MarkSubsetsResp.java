package com.xgls.web.vo.dataset;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MarkSubsetsResp", description = "批量标记结果")
public class MarkSubsetsResp {

    @Schema(description = "更新为任务目标子集（type_mark=0）的行数")
    private int targetUpdated;

    @Schema(description = "更新为预训练子集（type_mark=1）的行数")
    private int trainUpdated;

    @Schema(description = "写入 task_dataset 的记录条数")
    private int taskInserted;
}
