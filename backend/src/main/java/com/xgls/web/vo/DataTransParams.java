package com.xgls.web.vo;

import cn.hutool.json.JSONObject;
import lombok.Data;

@Data
public class DataTransParams {
    private Integer id;
    private Integer alg_id;
    private JSONObject params;
    private String alg_name;
}
