package com.xgls.web.vo;

import java.io.Serializable;
import java.util.List;

import com.xgls.web.entity.TrainLabel;

import lombok.Data;

@Data
public class ValParams implements Serializable {
    private Integer id;
    private List<TrainLabel> labels;
    private List<TrainItem> data;
    private String run_name;
    private String weights;
    private Integer batch_size;
    private Integer img_size;
    private Double conf_thres;
    private String ext_params;
    private String val_name;
    private String device;
    private String predict_name;
    private Boolean is_val;
    private Boolean save_txt;
}
