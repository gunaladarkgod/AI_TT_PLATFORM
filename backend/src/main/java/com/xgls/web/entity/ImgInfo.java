package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName("img_info")
@Data
public class ImgInfo implements Serializable {

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

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;
}
