package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("original_dataset")
public class OriginalDataset {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // 基本信息
    @TableField("name")
    private String name;

    @TableField("sensor_type")
    private String sensorType;

    @TableField("target_type")
    private String targetType;

    @TableField("img_num")
    private Integer imgNum;

    @TableField("anno_num")
    private Integer annoNum;

    @TableField("class_num")
    private Integer classNum;

    // 数据库为 JSON，这里直接用 String 映射（需要时前端自行 JSON.parse）
    @TableField("class_list")
    private String classList;

    // 0-Platform；1-CVAT；2-COCO
    @TableField("data_format")
    private Integer dataFormat;

    @TableField("username")
    private String username;

    @TableField("data_path")
    private String dataPath;

    @TableField("anno_path")
    private String annoPath;

    // 0-预训练子集；1-任务目标子集
    @TableField("type_mark")
    private Integer typeMark;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    // 关联信息
    @TableField("project_id")
    private Long projectId;

    @TableField("project_name")
    private String projectName;

    // “_” 分割的字符串
    @TableField("task_id")
    private String taskId;

    @TableField("task_name")
    private String taskName;
}
