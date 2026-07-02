package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolve data directories from the actual checked-out workspace, without hard-coded OS paths. */
public final class WorkspacePathUtil {
    private WorkspacePathUtil() {}

    public static Path workspaceRoot() {
        String override = StrUtil.trim(System.getenv("APP_WORKSPACE_ROOT"));
        if (StrUtil.isNotBlank(override)) {
            return Paths.get(override).toAbsolutePath().normalize();
        }

        Path start = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            boolean hasBackend = Files.isDirectory(candidate.resolve("backend"));
            boolean hasFrontend = Files.isDirectory(candidate.resolve("fronternd"));
            boolean hasMmdet = Files.isDirectory(candidate.resolve("mmdet_run"));
            if (hasBackend && (hasFrontend || hasMmdet)) {
                return candidate;
            }
        }
        if (start.getFileName() != null && "backend".equalsIgnoreCase(start.getFileName().toString())
                && start.getParent() != null) {
            return start.getParent();
        }
        return start;
    }

    /** 原始数据集登记文件跟随当前工作区；原始图片本身仍可放在任意外部目录。 */
    public static Path originalDatasetRoot() {
        return workspaceRoot().resolve("data").resolve("original_dataset").normalize();
    }

    /** 中间实例数据集固定跟随当前工作区，便于整项目迁移到不同设备。 */
    public static Path instanceDatasetMidRoot() {
        return workspaceRoot().resolve("data").resolve("instance_dataset_mid").normalize();
    }

    /** 最终实例数据集固定跟随当前工作区，便于整项目迁移到不同设备。 */
    public static Path instanceDatasetRoot() {
        return workspaceRoot().resolve("data").resolve("instance_dataset").normalize();
    }
}