package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Resolve data directories from the actual checked-out workspace, without hard-coded OS paths. */
public final class WorkspacePathUtil {
    private WorkspacePathUtil() {}

    public static Path workspaceRoot() {
        String override = StrUtil.trim(System.getenv("APP_WORKSPACE_ROOT"));
        if (StrUtil.isNotBlank(override)) {
            return Paths.get(override).toAbsolutePath().normalize();
        }

        Path start = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> starts = new ArrayList<>();
        starts.add(start);
        try {
            URI location = WorkspacePathUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codeLocation = Paths.get(location).toAbsolutePath().normalize();
            starts.add(Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent());
        } catch (Exception ignore) {
        }

        for (Path probe : starts) {
            for (Path candidate = probe; candidate != null; candidate = candidate.getParent()) {
                boolean hasBackend = Files.isDirectory(candidate.resolve("backend"));
                boolean hasFrontend = Files.isDirectory(candidate.resolve("fronternd"));
                boolean hasMmdetEngine = Files.isDirectory(candidate.resolve("engines").resolve("mmdet_run"));
                if (hasBackend && (hasFrontend || hasMmdetEngine)) {
                    return candidate;
                }
            }
        }
        if (start.getFileName() != null && "backend".equalsIgnoreCase(start.getFileName().toString())
                && start.getParent() != null) {
            return start.getParent();
        }
        return start;
    }

    /** 配置为相对路径时统一相对于项目根目录解析；绝对路径仅用于用户显式外部挂载。 */
    public static Path resolveConfiguredPath(String configured, String defaultRelative) {
        String value = StrUtil.blankToDefault(StrUtil.trim(configured), defaultRelative);
        Path path = Paths.get(value).normalize();
        return (path.isAbsolute() ? path : workspaceRoot().resolve(path)).toAbsolutePath().normalize();
    }

    /** 原始数据集登记文件跟随当前工作区；原始图片本身仍可放在任意外部目录。 */
    public static Path originalDatasetRoot() {
        return resolveConfiguredPath("data/original_dataset", "data/original_dataset");
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
