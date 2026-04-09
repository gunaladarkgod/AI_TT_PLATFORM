package com.xgls.web.vo.query;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.xgls.web.entity.EngineProject;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "EngineProjectQuery", description = "标注项目查询类")
public class EngineProjectQuery extends EngineProject {
    @Schema(description = "分页大小")
    private Long size;
    @Schema(description = "当前页号")
    private Long current;
    @Schema(description = "排序条件")
    private List<OrderItem> orders;
    @Schema(description = "单标签查询")
    private String query_label;
    @Schema(description = "多标签查询")
    private String query_labels;
}
