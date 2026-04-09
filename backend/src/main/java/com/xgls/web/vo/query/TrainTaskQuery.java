package com.xgls.web.vo.query;

import java.util.List;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.xgls.web.entity.TrainTask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "TrainTaskQuery", description = "训练任务查询类")
public class TrainTaskQuery extends TrainTask {
    @Schema(description = "分页大小")
    private Long size;
    @Schema(description = "当前页号")
    private Long current;
    @Schema(description = "排序条件")
    private List<OrderItem> orders;

    private LocalDateTime start_time;
    private LocalDateTime end_time;
}
