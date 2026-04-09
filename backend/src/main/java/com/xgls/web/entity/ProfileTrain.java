package com.xgls.web.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("profile_train")
@Schema(name = "ProfileTrain", description = "模型训练模板类")
public class ProfileTrain implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "名称")
    private String name;
    @Schema(description = "训练算法id")
    private Integer alg_id;

    private Integer weights;// 0-代表使用自定义文件
    private Integer hyp;
    private Integer cfg;
    private Integer batch_size;
    private Integer img_size;
    
    private Integer img_w;
    private Integer img_h;
    private Integer epoch;
    private Integer period;

    private String device;
    // 自训自测比例,不使用
    private Integer val_ratio;

    // 小标签过滤
    private Integer f_max; // 长边限制 0-代表不过滤
    private Integer f_min; // 短边限制 0-代表不过滤
    private Integer f_area; // 面积限制 0-代表不过滤

    @Schema(description = "备注")
    private String remark;
}
