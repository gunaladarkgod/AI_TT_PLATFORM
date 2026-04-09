package com.xgls.web.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("sys_user")
@Schema(name = "User", description = "用户实体类")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "密码")
    private String pmd;

    @Schema(description = "用户类型 1-系统管理员  2-管理员(暂时不用)  3-普通用户")
    private Integer type;
    @Schema(description = "账户状态:0-正常 1-锁定")
    private Integer status;
    @Schema(description = "添加时间, 格式:yyyy-MM-dd hh:mm:ss")
    private String addtime;

    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "部门")
    private String part;
    @Schema(description = "头像")
    private String head;
    @Schema(description = "备注")
    private String remark;

    @TableField(exist = false)
    private boolean isExpir;// token 是否过期

}
