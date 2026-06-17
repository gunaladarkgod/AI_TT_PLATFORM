package com.xgls.web.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.hutool.core.util.StrUtil;

/**
 * 本地存在 env.clearml.local 时校验解析（不报密钥内容）。
 */
class ClearmlEnvFileSupportTest {

    @Test
    void parseRepoEnvFileWhenPresent() throws Exception {
        Path env = resolveEnvFile();
        assumeTrue(Files.isRegularFile(env), "skip: no env.clearml.local at " + env.toAbsolutePath());

        Map<String, Object> map = ClearmlEnvFileSupport.parseDotEnv(env);
        String ak = nz(map.get("CLEARML_API_ACCESS_KEY"));
        assertFalse(StrUtil.isBlank(ak));
        assertTrue(ak.length() >= 8);

        Path found = ClearmlEnvFileSupport.findEnvClearmlLocal();
        assertNotNull(found, "findEnvClearmlLocal should locate env file when cwd matches repo layout");
    }

    private static String nz(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Path resolveEnvFile() {
        String root = System.getProperty("user.dir");
        Path a = Paths.get(root, "backend", "env.clearml.local");
        Path b = Paths.get(root, "env.clearml.local");
        if (Files.isRegularFile(a)) {
            return a;
        }
        if (Files.isRegularFile(b)) {
            return b;
        }
        return a;
    }
}
