package com.xgls.web.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 训练前实例数据集可用性：含全部库中记录、是否可选训练、原因说明。
 */
@Data
public class InstanceDatasetTrainingReadinessDto {
    private Long id;
    private String name;
    private String fatherName;
    private boolean qualified;
    private String exportFormat;
    private boolean cocoAvailable;
    private boolean yoloAvailable;
    private String dataYaml;
    private List<String> reasons = new ArrayList<>();
}
