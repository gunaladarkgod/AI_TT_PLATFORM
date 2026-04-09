package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName("anno_info")
@Data
public class AnnoInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_id")
    private Long projectId;

    @TableField("task_id")
    private Long taskId;

    @TableField("img_name")
    private String imgName;

    @TableField("data_stage")
    private Integer dataStage;

    @TableField("class_name")
    private String className;

    @TableField("x1")
    private Double x1;

    @TableField("y1")
    private Double y1;

    @TableField("x2")
    private Double x2;

    @TableField("y2")
    private Double y2;

    @TableField("x3")
    private Double x3;

    @TableField("y3")
    private Double y3;

    @TableField("x4")
    private Double x4;

    @TableField("y4")
    private Double y4;
}
