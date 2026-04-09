package com.xgls.web.service;

import com.xgls.web.entity.OriginalDataset;

import java.util.List;

public interface OriginalDataset1Service {
    List<OriginalDataset> getAllOriginalDatasets();
    List<OriginalDataset> listByIds(List<Long> ids);
}
