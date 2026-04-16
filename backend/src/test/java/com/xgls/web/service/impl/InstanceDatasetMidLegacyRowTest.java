package com.xgls.web.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xgls.web.entity.InstanceDataset;
import org.junit.jupiter.api.Test;

class InstanceDatasetMidLegacyRowTest {

    @Test
    void midLikeWhenPathsSetAndNoPreprocessMetadata() {
        InstanceDataset d = new InstanceDataset();
        d.setTrainImagePath("/a/train/images/");
        d.setTrainAnnoPath("/a/train/anno/");
        d.setTestImagePath("/a/test/images/");
        d.setTestAnnoPath("/a/test/anno/");
        d.setConfigList(null);
        d.setParamSchema(null);
        assertTrue(InstanceDatasetMidServiceImpl.isMidLikeLegacyInstanceDataset(d));
    }

    @Test
    void notMidLikeWhenPreprocessConfigListPresent() {
        InstanceDataset d = new InstanceDataset();
        d.setTrainImagePath("/a/train/images/");
        d.setTrainAnnoPath("/a/train/anno/");
        d.setTestImagePath("/a/test/images/");
        d.setTestAnnoPath("/a/test/anno/");
        d.setConfigList("[{\"order\":1,\"name\":\"enhance\"}]");
        assertFalse(InstanceDatasetMidServiceImpl.isMidLikeLegacyInstanceDataset(d));
    }
}
