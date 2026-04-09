package com.xgls.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("new_preprocess_scriptinfo") // 对应数据库表名
public class PreprocessScriptInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;           // 总脚本表的主键，唯一标识测试信息

    @TableField("name")
    private String name;          // 脚本名字

//    @TableField("result_path")
//    private String result_path;    // 脚本处理结果路径，由后端计算

    @TableField("script_path")
    private String script_path;    // 脚本所在路径，由后端计算

    @TableField("type")
    private Integer type;         // 脚本类型，0：增强 1：增广

    @TableField("param_schema")
    private String paramSchema; // JSON 字符串，描述脚本所需参数

    // ========== 新增字段 ==========
    @TableField("uploader")
    private String uploader; // 脚本上传者用户名
}