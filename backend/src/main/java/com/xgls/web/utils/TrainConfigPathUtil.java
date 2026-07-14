package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;

import java.nio.file.Files;
import java.nio.file.Path;

/** 新版 MMDet 单文件配置路径解析。 */
public final class TrainConfigPathUtil {
    private TrainConfigPathUtil() {}

    public static Path resolveConfig(Path myfilesRoot, String taskName) {
        if (myfilesRoot == null || StrUtil.isBlank(taskName)) return null;
        Path modelRoot = myfilesRoot.toAbsolutePath().normalize().resolve("modelcfg").normalize();
        Path config = modelRoot.resolve(taskName.trim()).resolve("config.py").normalize();
        return config.startsWith(modelRoot) ? config : null;
    }

    public static Path findExistingConfig(Path myfilesRoot, String taskName) {
        Path config = resolveConfig(myfilesRoot, taskName);
        return config != null && Files.isRegularFile(config) ? config : null;
    }
}
