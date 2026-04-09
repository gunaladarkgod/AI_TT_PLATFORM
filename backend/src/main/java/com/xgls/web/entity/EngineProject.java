package com.xgls.web.entity;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("engine_project")
@Schema(name = "EngineProject", description = "标注项目类")
public class EngineProject implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键")
    private Integer id;
    private String name;
    // private String bug_tracker;
    private LocalDateTime created_date;
    private LocalDateTime updated_date;
    private String status;
    private Integer assignee_id;
    private Integer owner_id;
    private Integer organization_id;
    private Integer source_storage_id;
    private Integer target_storage_id;
    private LocalDateTime assignee_updated_date;

    /** 扩展字段 */
    private Integer del_flg;
    private LocalDateTime del_time;
    private String del_user;
    /** 拓展字段 */
    private String a_type; // 项目分组字段

    private String a_l; // vis/ir/sar 光源类型
    private String a_s; // cvat/dota/yolo 来源
    private String a_g; // test/train 数据用途
    private String a_n; // 名称

    /** 关联label */
    @TableField(exist = false)
    private List<EngineLabel> labels;

    /** 解析project名称 */
    public static void parsePName(EngineProject project) {
        String name = project.getName();
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
            project.setA_n(name);
        } else {
            project.setA_l(map.getOrDefault("l", ""));
            project.setA_s(map.getOrDefault("s", ""));
            project.setA_g(map.getOrDefault("g", ""));
            project.setA_n(map.getOrDefault("n", name));
            project.setName(project.getA_n());
        }
    }
}
