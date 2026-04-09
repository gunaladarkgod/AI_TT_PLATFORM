// com.xgls.web.service.InstanceDatasetinfoService.java
package com.xgls.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xgls.web.entity.InstanceDatasetinfo;

import java.util.List;

public interface InstanceDatasetinfoService extends IService<InstanceDatasetinfo> {

    // 你原来自己的方法，继续保留
    List<InstanceDatasetinfo> getAllInstanceDatasets();

    /**
     * 根据 ID 删除实例数据集（包括数据库记录和本地文件）
     *
     * @param id 实例数据集的主键 ID
     * @return 删除成功返回 true，记录不存在或删除失败返回 false
     */
    boolean deleteInstanceDatasetById(Long id);
}