// com.xgls.web.service.PreprocessScriptInfoService.java
package com.xgls.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgls.web.entity.PreprocessScriptInfo;
import java.util.List;

/**
 * 预处理脚本信息服务接口
 * 继承 IService 后，自动获得 save, update, remove, get 等通用方法
 */
public interface PreprocessScriptInfoService
        extends IService<PreprocessScriptInfo> {

    /**
     * 根据类型查询脚本列表
     * @param type 脚本类型（0:增强, 1:增广）
     * @return 脚本列表
     */
    List<PreprocessScriptInfo> getScriptsByType(Integer type);
}