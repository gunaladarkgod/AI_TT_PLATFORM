package com.xgls.web.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("train_yolo_file")
@Schema(name = "TrainYoloFile", description = "训练配置文件管理类")
public class TrainYoloFile implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    // 文件类型 weights hyp cfg
    private String type;
    // 文件名称
    private String name;
    // 文件保存路径
    private String path;
    // 创建时间
    private LocalDateTime created_date;
    // 备注
    private String remark;
}
