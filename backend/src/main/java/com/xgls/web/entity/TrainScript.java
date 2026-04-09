package com.xgls.web.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("train_script")
@Schema(name = "TrainScript", description = "算法管理类")
public class TrainScript implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    @Schema(description = "算法类型 train/trans/data")
    private String type;

    @Schema(description = "算法名称")
    private String name;
    @Schema(description = "运行环境")
    private String env;
    @Schema(description = "运行指令")
    private String cmd;
    @Schema(description = "训练入口")
    private String main;

    @Schema(description = "验证入口")
    private String val;
    @Schema(description = "推理入口")
    private String detect;

    @Schema(description = "后缀 .sh or  .bat")
    private String suff;

    @Schema(description = "更新时间, 格式:yyyy-MM-dd hh:mm:ss")
    private LocalDateTime uptime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "转换配置参数")
    private String cfg;

}
