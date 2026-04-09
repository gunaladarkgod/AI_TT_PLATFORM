package com.xgls.web.entity;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.util.HashMap;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("engine_task")
@Schema(name = "EngineTask", description = "标注任务类")
public class EngineTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    private String name;
    private String mode;
    private LocalDateTime created_date;
    private LocalDateTime updated_date;
    private String status;
    // private String bug_tracker;
    private Integer owner_id;
    private String overlap;
    private Integer assignee_id;
    private Integer segment_size;
    private Long size;// 20241213 新增
    private Integer project_id;
    private Integer data_id;
    private String dimension;
    private String subset;
    private Integer organization_id;
    private Integer source_storage_id;
    private Integer target_storage_id;
    private LocalDateTime assignee_updated_date;

    /** 扩展字段 */
    private Integer del_flg;// 删除标记
    private LocalDateTime del_time;// 删除时间
    private String del_user;// 删除用户
    private Integer export_img;// 导出标记
    private LocalDateTime export_time;// 导出时间
    private String export_label;// 导出标签
    private String first_img; // 第一张图片
    /** export 状态 */
    private Integer export_status; // 导出状态
    private Integer export_type; // 是否导出图片 0-不导出 1-导出
    private Long export_queue; // 入队时间,优先级

    private String a_s;// 厂家 weibo bilibili other
    private String a_r; // 分辨率(按宽度) 不统一填xxx 4096/2048
    private String a_t; // 按原始视频放置时间 20240925
    private String a_v; // 场地 baotou/jichang
    private String a_p; // 视角 d无人机 s卫星 u仰视 h平视
    private String a_e; // 场景 city snow desert grass
    private String a_a; // 精度 0.5m 1m 1.5m 2m
    private String a_n; // 名称

    private String a_se; // p-bm 其他

    /** 解析task名称 */
    public static void parseTName(EngineTask task) {
        String name = task.getName();
        if (StrUtil.isBlank(name)) {
            return;
        }
        name = name.toLowerCase();
        String[] kv_arr = name.split("_");
        HashMap<String, String> map = new HashMap<>();
        for (int i = 0; i < kv_arr.length; i++) {
            String[] kv = kv_arr[i].split("=");
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        if (map.get("n") == null) {
            task.setA_n(name);
        } else {
            task.setA_s(map.getOrDefault("s", ""));
            task.setA_r(map.getOrDefault("r", ""));
            task.setA_t(map.getOrDefault("t", ""));
            task.setA_v(map.getOrDefault("v", ""));
            task.setA_p(map.getOrDefault("p", ""));
            task.setA_e(map.getOrDefault("e", ""));
            task.setA_a(map.getOrDefault("a", ""));
            task.setA_se(map.getOrDefault("se", ""));
            task.setA_n(map.getOrDefault("n", name));
            task.setName(task.getA_n());
        }
    }

    private Integer data_trans;// 是否正在转换 0-非 1-正在转换中
}
