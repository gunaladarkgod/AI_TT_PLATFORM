package com.xgls.web.vo;

import java.util.List;

import lombok.Data;

@Data
public class TrainItem {
    private Integer pid;
    private List<String> labels;
    private List<Integer> tasks;
}
