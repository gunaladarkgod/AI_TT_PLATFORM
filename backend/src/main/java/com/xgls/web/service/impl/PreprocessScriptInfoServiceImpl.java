// com.xgls.web.service.impl.PreprocessScriptInfoServiceImpl.java
package com.xgls.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.PreprocessScriptInfo;
import com.xgls.web.mapper.PreprocessScriptInfoMapper;
import com.xgls.web.service.PreprocessScriptInfoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 预处理脚本信息服务实现类
 * 继承 ServiceImpl 后，自动获得 MyBatis-Plus 提供的通用 CRUD 能力（如 save, update, remove 等）
 */
@Service
public class PreprocessScriptInfoServiceImpl
        extends ServiceImpl<PreprocessScriptInfoMapper, PreprocessScriptInfo>
        implements PreprocessScriptInfoService {

    @Override
    public List<PreprocessScriptInfo> getScriptsByType(Integer type) {
        // 使用 QueryWrapper 查询指定类型的脚本
        QueryWrapper<PreprocessScriptInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type);
        return this.list(wrapper);
    }
}