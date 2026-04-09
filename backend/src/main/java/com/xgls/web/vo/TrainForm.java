package com.xgls.web.vo;

import com.xgls.web.entity.TrainArgs;
import com.xgls.web.entity.TrainData;

import lombok.Data;

@Data
public class TrainForm {
    private Integer id;
    private String name;
    private String type;
    private TrainArgs args;
    private TrainData data;

    private Integer cls_num;// 标签类别数
    private Integer prj_num;// project数
    private Integer task_num;// task数
    private Integer img_num;// 图片数
    private Long obj_num;// 标注数目

    private String remark;//备注信息

    private String cmd; //明确指令类型  

    private Integer img_val_num;// 图片数验证集
    private Long obj_val_num;// 标注数目验证集
}
