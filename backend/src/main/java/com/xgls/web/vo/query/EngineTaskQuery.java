package com.xgls.web.vo.query;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.xgls.web.entity.EngineTask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "EngineTaskQuery", description = "标注任务查询类")
public class EngineTaskQuery extends EngineTask {
    // @Schema(description = "分页大小") 和内部属性冲突了,直接用内部size
    // private Long size;
    @Schema(description = "当前页号")
    private Long current;
    @Schema(description = "排序条件")
    private List<OrderItem> orders;

    private String start_time;
    private String end_time;
    @Schema(description = "项目id列表,逗号分隔")
    private String project_ids;
}
