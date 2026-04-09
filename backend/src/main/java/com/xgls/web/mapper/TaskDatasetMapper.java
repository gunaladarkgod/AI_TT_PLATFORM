package com.xgls.web.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xgls.web.entity.TaskDataset;

@Mapper
public interface TaskDatasetMapper extends BaseMapper<TaskDataset> {

}