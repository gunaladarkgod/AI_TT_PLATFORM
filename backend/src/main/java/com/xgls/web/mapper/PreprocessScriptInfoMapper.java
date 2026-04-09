package com.xgls.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xgls.web.entity.PreprocessScriptInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PreprocessScriptInfoMapper extends BaseMapper<PreprocessScriptInfo> {
    // BaseMapper 已经提供了基本 CRUD，无需额外方法
}