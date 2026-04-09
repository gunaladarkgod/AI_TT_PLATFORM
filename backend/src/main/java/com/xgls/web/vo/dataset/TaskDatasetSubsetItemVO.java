package com.xgls.web.vo.dataset;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "任务数据集子集条目")
public class TaskDatasetSubsetItemVO {

    @Schema(description = "子集ID")
    private Long id;

    @JsonProperty("name")
    @Schema(description = "子集名称")
    private String name;

    @JsonProperty("sensor_type")
    @Schema(description = "传感器类型")
    private String sensorType;

    @JsonProperty("class_list")
    @Schema(description = "类别列表")
    private List<String> classList;

    @JsonProperty("data_path")
    @Schema(description = "数据路径")
    private String dataPath;

    @JsonProperty("anno_path")
    @Schema(description = "标注路径")
    private String annoPath;

    @JsonProperty("img_num")
    @Schema(description = "图片数量")
    private Integer imgNum;

    @JsonProperty("anno_num")
    @Schema(description = "标注数量")
    private Integer annoNum;
}