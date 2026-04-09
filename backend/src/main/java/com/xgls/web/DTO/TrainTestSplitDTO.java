package com.xgls.web.dto;

import lombok.Data;
import java.util.List;

@Data
public class TrainTestSplitDTO {
    private Long taskId;
    private List<List<String>> testPlans;
    private List<String> trainOriginalIds;
}