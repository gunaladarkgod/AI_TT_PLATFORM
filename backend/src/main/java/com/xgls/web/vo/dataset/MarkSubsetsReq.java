package com.xgls.web.vo.dataset;

import lombok.Data;

import java.util.List;

@Data
public class MarkSubsetsReq {
    /** 被选为“目标子集”的 original_dataset.id 列表（本次更新为 type_mark=1） */
    private List<Long> target;

    /** 被选为“预训练子集”的 original_dataset.id 列表（本次更新为 type_mark=0） */
    private List<Long> train;

    /** 新生成的 TaskDataset 名称（推荐用这个） */
    private String name;

    /** 兼容老前端字段：如未提供 name，则回退使用 fatherName */
    private String fatherName;
}
