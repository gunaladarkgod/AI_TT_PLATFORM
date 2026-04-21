package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("task_dataset")
@Schema(name = "TaskDataset", description = "任务数据集信息实体")
public class TaskDataset implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("name")
    @Schema(description = "数据集名称")
    private String name;

    @TableField("sensor_type")
    @Schema(description = "传感器类型")
    private String sensorType;

    @TableField("target_type")
    @Schema(description = "目标类型")
    private String targetType;

    @TableField("data_format")
    @Schema(description = "数据格式（0-Platform；1-CVAT；2-COCO）")
    private Integer dataFormat;

    @TableField("username")
    @Schema(description = "创建人")
    private String username;

    @TableField("created_time")
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    // ===== 核心（目标）子集 =====
    @TableField("core_id")
    @Schema(description = "目标子集中的元素id列表（下划线拼接）")
    private String coreId;

    @TableField("core_name")
    @Schema(description = "目标子集中的元素名列表（下划线拼接）")
    private String coreName;

    @TableField("core_target_type")
    @Schema(description = "目标子集目标类型（舰船/车辆/飞机/其他/复合）")
    private String coreTargetType;

    @TableField("core_img_num")
    @Schema(description = "目标子集图片数量之和")
    private Integer coreImgNum;

    @TableField("core_anno_num")
    @Schema(description = "目标子集标注数量之和")
    private Integer coreAnnoNum;

    @TableField("core_class_num")
    @Schema(description = "目标子集类别数量")
    private Integer coreClassNum;

    @TableField("core_class_list")
    @Schema(description = "目标子集合并后的类别统计（JSON字典字符串）")
    private String coreClassList;

    @TableField("core_data_path")
    @Schema(description = "目标子集数据路径")
    private String coreDataPath;

    @TableField("core_anno_path")
    @Schema(description = "目标子集标注路径")
    private String coreAnnoPath;

    // ===== 预训练（辅助）子集 =====
    @TableField("sup_id")
    @Schema(description = "预训练子集中的元素id列表（下划线拼接）")
    private String supId;

    @TableField("sup_name")
    @Schema(description = "预训练子集中的元素名列表（下划线拼接）")
    private String supName;

    @TableField("sup_target_type")
    @Schema(description = "预训练子集目标类型（舰船/车辆/飞机/其他/复合）")
    private String supTargetType;

    @TableField("sup_img_num")
    @Schema(description = "预训练子集图片数量之和")
    private Integer supImgNum;

    @TableField("sup_anno_num")
    @Schema(description = "预训练子集标注数量之和")
    private Integer supAnnoNum;

    @TableField("sup_class_num")
    @Schema(description = "预训练子集类别数量")
    private Integer supClassNum;

    @TableField("sup_class_list")
    @Schema(description = "预训练子集合并后的类别统计（JSON字典字符串）")
    private String supClassList;

    @TableField("sup_data_path")
    @Schema(description = "预训练子集数据路径")
    private String supDataPath;

    @TableField("sup_anno_path")
    @Schema(description = "预训练子集标注路径")
    private String supAnnoPath;

    // ===== Dev 任务定义（与 task_dataset_dev 统一）=====
    @TableField("task_desc")
    @Schema(description = "Dev任务描述")
    private String taskDesc;

    @TableField("target_schema")
    @Schema(description = "目标类别定义(JSON数组字符串)")
    private String targetSchema;

    @TableField("test_datasets")
    @Schema(description = "测试数据集名称列表(JSON数组字符串)")
    private String testDatasets;

    @TableField("mapping_rules")
    @Schema(description = "类别映射规则(JSON对象字符串)")
    private String mappingRules;

    @TableField("dev_updated_time")
    @Schema(description = "Dev任务定义更新时间")
    private LocalDateTime devUpdatedTime;

    @TableField("dev_updated_by")
    @Schema(description = "Dev任务定义更新人")
    private String devUpdatedBy;

    @TableField("last_export_time")
    @Schema(description = "最近导出中间实例数据集时间")
    private LocalDateTime lastExportTime;

    @TableField("last_export_source_updated_time")
    @Schema(description = "最近导出时对应的源定义更新时间")
    private LocalDateTime lastExportSourceUpdatedTime;

    @TableField("last_export_by")
    @Schema(description = "最近导出人")
    private String lastExportBy;

    @TableField("last_export_mid_count")
    @Schema(description = "最近导出产生的中间实例数据集数量")
    private Integer lastExportMidCount;
}
