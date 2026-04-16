package com.xgls.web.service;

import com.xgls.web.entity.InstanceDataset;

import java.util.List;
import java.util.Map;

public interface PreprocessService {

    /**
     * 执行预处理：自动命名，支持多个源实例数据集
     *
     * @param sourceInstanceIds 源实例数据集 ID 列表
     * @param enhanceScriptId   增强脚本 ID
     * @param enhanceParams     增强参数
     * @param augmentScriptId   增广脚本 ID
     * @param augmentParams     增广参数
     * @return 生成的实例数据集列表
     * @throws Exception 预处理异常
     */
    List<InstanceDataset> runPreprocess(
            List<Long> sourceInstanceIds,
            Integer enhanceScriptId,
            Map<String, Object> enhanceParams,
            Integer augmentScriptId,
            Map<String, Object> augmentParams
    ) throws Exception;
}