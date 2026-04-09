package com.xgls.web.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;

import cn.hutool.core.io.FileUtil;

public interface TransFormUtils extends Library {

    TransFormUtils INSTANCE = (TransFormUtils) Native
            .load(FileUtil.isWindows() ? "transform_utils.dll" : "transform_utils.so", TransFormUtils.class);

    int transform_directory(String dirPath);

    int transform_file(String filePath);
}
