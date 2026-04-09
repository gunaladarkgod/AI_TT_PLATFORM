package com.xgls.web.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xgls.web.entity.EngineLabel;
import com.xgls.web.entity.TrainLabel;

public interface EngineLabelMapper extends BaseMapper<EngineLabel> {
    /** 查所有标签名,去重,如果pid部位null,则不包含 */
    List<String> queryDistinctNames(Integer project_id);

    /** 查新增的原始标签名 */
    List<TrainLabel> queryNewLabels();

    /** 查包含标签的所有项目id集合,多个标签or的关系 */
    List<Integer> queryProjectsByOrLabels(List<String> labels);

    /**
     * 查所有项目的id,name,labels
     *
     */
    List<Map<String, Object>> queryProjectLabels(@Param("a_l") String a_l, @Param("a_s") String a_s,
            @Param("a_g") String a_g, @Param("user_id") Long user_id);
}
