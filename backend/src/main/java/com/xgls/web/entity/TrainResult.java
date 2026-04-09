package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("train_result")
@Schema(name = "TrainResult", description = "训练结果实体类")
public class TrainResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("task_id")
    private Integer taskId;

    @TableField("task_name")
    private String taskName;

    @TableField("user_name")
    private String userName;

    @TableField("model_type")
    private String modelType;

    @TableField("dataset")
    private String dataset;

    // 若你在 SQL 里用的是 `finish_time`
    @TableField("finish_time")
    private LocalDateTime time;

    @TableField("map")
    private Double map;

    @TableField("ap50")
    private Double ap50;

    @TableField("ap75")
    private Double ap75;

    @TableField("aps")
    private Double aps;

    @TableField("apm")
    private Double apm;

    @TableField("apl")
    private Double apl;


    @TableField("network_name")
    private String networkName;
}
