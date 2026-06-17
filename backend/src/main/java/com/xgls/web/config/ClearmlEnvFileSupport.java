package com.xgls.web.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.StrUtil;

/**
 * 读取仓库内的 {@code env.clearml.local}（KEY=value）。供 EnvironmentPostProcessor 与 {@link ClearmlProperties}
 * 共用，避免仅靠 YAML 占位符时解析顺序导致密钥未注入。
 */
public final class ClearmlEnvFileSupport {

    private ClearmlEnvFileSupport() {}

    /** JVM 参数例：{@code -Dclearml.env.file=/abs/path/env.clearml.local} */
    public static final String SYSTEM_PROPERTY_PATH = "clearml.env.file";

    /**
     * 查找本地 ClearML env 文件（优先 {@link #SYSTEM_PROPERTY_PATH}）。
     */
    public static Path findEnvClearmlLocal() {
        String prop = System.getProperty(SYSTEM_PROPERTY_PATH);
        if (StrUtil.isNotBlank(prop)) {
            Path abs = Paths.get(prop.trim()).toAbsolutePath().normalize();
            if (Files.isRegularFile(abs)) {
                return abs;
            }
        }
        String userDir = System.getProperty("user.dir", ".");
        List<Path> candidates =
                List.of(
                        Paths.get(userDir, "env.clearml.local"),
                        Paths.get(userDir, "backend", "env.clearml.local"),
                        Paths.get(userDir, "..", "backend", "env.clearml.local").normalize());
        for (Path p : candidates) {
            Path abs = p.toAbsolutePath().normalize();
            if (Files.isRegularFile(abs)) {
                return abs;
            }
        }
        return null;
    }

    public static Map<String, Object> parseDotEnv(Path path) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if ((val.startsWith("\"") && val.endsWith("\""))
                    || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }
            if (!key.isEmpty()) {
                out.put(key, val);
            }
        }
        return out;
    }
}
