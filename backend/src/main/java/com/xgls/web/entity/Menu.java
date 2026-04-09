package com.xgls.web.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("sys_menu")
@Schema(name = "Menu", description = "菜单类")
public class Menu implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    @Schema(description = "菜单路径")
    private String url;
    @Schema(description = "菜单标题")
    private String name;
    @Schema(description = "菜单排序字段")
    private Integer order_num;

    @Schema(description = "是否隐藏")
    private Integer is_hidden;

    @Schema(description = "父级id,默认0,代表无")
    private Integer parent_id;
}
