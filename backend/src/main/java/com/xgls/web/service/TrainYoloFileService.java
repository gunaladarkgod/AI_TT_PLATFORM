package com.xgls.web.service;

import java.io.IOException;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.entity.TrainYoloFile;
import com.xgls.web.mapper.TrainYoloFileMapper;

@Service
public class TrainYoloFileService extends ServiceImpl<TrainYoloFileMapper, TrainYoloFile> {

    @Transactional
    public boolean saveLink(TrainYoloFile record, MultipartFile file, String dir, String suff)
            throws IllegalStateException, IOException {
        if (save(record)) {
            file.transferTo(Paths.get(dir, record.getType() + "_" + record.getId() + suff));
            return true;
        }
        return false;
    }

}
