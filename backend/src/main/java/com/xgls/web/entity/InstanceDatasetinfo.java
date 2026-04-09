package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实例数据集信息实体类（预处理结果）
 * 对应数据库表：instance_datasetinfo
 * 用于存储经过预处理（增强/增广）后生成的实例数据集，包含原始数据字段 + 预处理链路与参数记录
 */
@TableName("instance_datasetinfo")
@Data
public class InstanceDatasetinfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务数据集名称
     */
    @TableField("father_name")
    private String fatherName;

    /**
     * 当前实例数据集名称
     */
    @TableField("name")
    private String name;

    /**
     * 传感器类型（如：可见光、SAR、红外等）
     */
    @TableField("sensor_type")
    private String sensorType;

    /**
     * 目标类型（如：车辆、船舶、飞机等）
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 图片数量
     */
    @TableField("img_num")
    private Integer imgNum;

    /**
     * 标注数量（样本数量）
     */
    @TableField("anno_num")
    private Integer annoNum;

    /**
     * 类别数量
     */
    @TableField("class_num")
    private Integer classNum;

    /**
     * 类别列表，JSON 格式字符串，例如：["car", "truck"]
     */
    @TableField("class_list")
    private String classList;

    /**
     * 训练集图像路径
     */
    @TableField("train_image_path")
    private String trainImagePath;

    /**
     * 训练集标注路径
     */
    @TableField("train_anno_path")
    private String trainAnnoPath;

    /**
     * 测试集图像路径
     */
    @TableField("test_image_path")
    private String testImagePath;

    /**
     * 测试集标注路径
     */
    @TableField("test_anno_path")
    private String testAnnoPath;

    /**
     * 数据格式（0-Platform；1-CVAT；2-COCO）
     */
    @TableField("data_format")
    private Integer dataFormat;

    /**
     * 创建人用户名
     */
    @TableField("username")
    private String username;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 最后更新时间（自动更新）
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

    /**
     * 预处理链路清单，JSON 格式，例如：
     * [{"order":1,"name":"旋转增强"},{"order":2,"name":"色彩增广"}]
     */
    @TableField("config_list")
    private String configList;

    /**
     * 实际使用的参数记录，JSON 格式，便于复现，例如：
     * {"rotate_angle": 30, "brightness_factor": 1.2}
     */
    @TableField("param_schema")
    private String paramSchema;
}