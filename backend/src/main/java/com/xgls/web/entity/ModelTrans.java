package com.xgls.web.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("model_trans")
@Schema(name = "ModelTrans", description = "模型转换类")
public class ModelTrans implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    @Schema(description = "转换任务名称")
    private String name;
    @Schema(description = "转换算法类型")
    private String type;
    @Schema(description = "源模型名称")
    private String weights;

    @Schema(description = "转换参数")
    private String params;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建者")
    private String createman;

    @Schema(description = "创建时间")
    private LocalDateTime createtime;

    @Schema(description = "开始时间")
    private LocalDateTime starttime;

    @Schema(description = "结束时间")
    private LocalDateTime endtime;

    @Schema(description = "备注")
    private String remark;
}
