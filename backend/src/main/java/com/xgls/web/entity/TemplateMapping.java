package com.xgls.web.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("template_mapping")
public class TemplateMapping {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("class_list")
    private String classList;

    @TableField("created_time")
    private LocalDateTime createdTime;
}