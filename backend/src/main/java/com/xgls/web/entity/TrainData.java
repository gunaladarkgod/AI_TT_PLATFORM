package com.xgls.web.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("train_data")
@Schema(name = "TrainData", description = "训练数据集类")
public class TrainData implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    private String labels;
    //训练集
    private String data;
    //测试集
    private String val;
}
