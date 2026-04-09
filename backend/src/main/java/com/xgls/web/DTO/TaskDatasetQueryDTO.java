package com.xgls.web.dto;

import lombok.Data;

@Data

public class TaskDatasetQueryDTO {
    private Integer current = 1;
    private Integer size = 10;
    private String name;
    private Integer type; // dataFormat
    private String createdBy; // username
}
