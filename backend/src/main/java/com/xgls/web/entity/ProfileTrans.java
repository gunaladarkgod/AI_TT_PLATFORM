package com.xgls.web.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("profile_trans")
@Schema(name = "ProfileTrans", description = "模型转换模板类")
public class ProfileTrans implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    @Schema(description = "转换模板名称")
    private String name;
    @Schema(description = "转换算法类型")
    private String type;

    @Schema(description = "转换参数")
    private String params;

    @Schema(description = "备注")
    private String remark;
}
