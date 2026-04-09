package com.xgls.web.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("train_task")
@Schema(name = "TrainTask", description = "训练任务类")
public class TrainTask implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    private String name;
    private String type;
    private LocalDateTime created_date;
    private LocalDateTime started_date;
    private LocalDateTime updated_date;
    private LocalDateTime finish_date;
    private Integer status;
    private Integer run_state;
    private String run_name;// 最后一个exp
    private String trans_name;// 最后一个转换的exp
    private String remark;
    // 统计信息
    private Integer cls_num;// 标签数
    private Integer prj_num;// project数
    private Integer task_num;// task数
    private Integer img_num;// 图片数
    private Long obj_num;// obj数据
    private Long enqueue;// 入队的时间 会作为优先级

    private String username;// 创建者

    private String val_name;// 当前验证任务名称
    private Integer val_state; // 0-可运行 1-运行中

    private String predict_name;// 当前预测任务名称
    private Integer predict_state; // 0-可运行 1-运行中

    @TableField(exist = false)
    private String msg_type;

}
