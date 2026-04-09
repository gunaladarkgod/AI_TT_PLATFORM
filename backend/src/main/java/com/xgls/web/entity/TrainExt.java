package com.xgls.web.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("train_ext")
@Schema(name = "TrainExt", description = "训练拓展参数类")
public class TrainExt implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键-训练任务id")
    private Integer id;
    @Schema(description = "拓展参数-json格式")
    private String params;
    @Schema(description = "拓展文件名称")
    private String file;
    @Schema(description = "更新时间")
    private LocalDateTime update_time;
}
