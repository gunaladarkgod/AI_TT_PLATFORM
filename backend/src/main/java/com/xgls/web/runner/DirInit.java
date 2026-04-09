package com.xgls.web.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xgls.web.base.CodeMap;

import lombok.extern.slf4j.Slf4j;

/**
 * 初始化创建系统必要的文件夹
 */
@Component
@Slf4j
@Order(2)
public class DirInit implements ApplicationRunner {
    @Value("${sys.root-upload}")
    String rootPath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String src = rootPath + CodeMap.DIR_SRC + "/";
        createDir(src + CodeMap.DIR_CVAT_TASK);
        createDir(src + CodeMap.DIR_YOLO);
        createDir(src + CodeMap.DIR_TRAIN_TASK);
        createDir(src + CodeMap.DIR_TEMP);
        createDir(src + CodeMap.DIR_SCRIPT);
        createDir(src + CodeMap.DIR_MODEL_TRANS);
        createDir(src + CodeMap.DIR_MODEL_CALIBRATE);
        createDir(src + CodeMap.DIR_MODEL_CALIBRATE + "/" + CodeMap.DIR_MODEL_CHECK);
        createDir(src + CodeMap.DIR_MODEL_CALIBRATE + "/" + CodeMap.DIR_MODEL_VAL);
    }

    private void createDir(String dirPath) {
        Path path = Paths.get(dirPath);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("create dir fail:{}", dirPath);
        }
    }
}
