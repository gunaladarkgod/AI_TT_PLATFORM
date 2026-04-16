package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 中间实例数据集实体。
 * 对应数据库表：instance_dataset_mid
 * 由任务数据集导出产生，作为预处理的输入。
 */
@TableName("instance_dataset_mid")
@Data
public class InstanceDatasetMid implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("father_name")
    private String fatherName;

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

    @TableField("class_list")
    private String classList;

    @TableField("train_image_path")
    private String trainImagePath;

    @TableField("train_anno_path")
    private String trainAnnoPath;

    @TableField("test_image_path")
    private String testImagePath;

    @TableField("test_anno_path")
    private String testAnnoPath;

    @TableField("data_format")
    private Integer dataFormat;

    @TableField("username")
    private String username;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
