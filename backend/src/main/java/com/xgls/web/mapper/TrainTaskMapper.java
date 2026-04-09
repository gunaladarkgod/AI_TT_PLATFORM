package com.xgls.web.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xgls.web.entity.TrainTask;

public interface TrainTaskMapper extends BaseMapper<TrainTask> {
    /** 查所有用户,去重 */
    List<String> queryDistinctUsernames();
}
