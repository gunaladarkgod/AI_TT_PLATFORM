package com.xgls.web.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/**
 * ClearML Server API（用于「模型训练（dev）」监控进行中实验；密钥仅存服务端）。
 *
 * <p>除 YAML / 环境变量外，在 Bean 初始化时合并 {@code env.clearml.local}，避免占位符解析顺序导致 access-key 为空。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sys.clearml")
public class ClearmlProperties {

    private static final Logger log = LoggerFactory.getLogger(ClearmlProperties.class);

    /** 是否启用 ClearML HTTP 调用（false 时不发起外网请求）。 */
    private boolean enabled = false;

    /** ClearML API Server，例如 https://api.clear.ml */
    private String apiHost = "https://api.clear.ml";

    /** ClearML Web App，用于拼接控制台链接，例如 https://app.clear.ml */
    private String webHost = "https://app.clear.ml";

    /** API 版本路径段，例如 2.23 → URL …/v2.23/… */
    private String apiVersion = "2.23";

    /** ClearML Access Key（「Workspace API Credentials」里显示的那条 KEY）。 */
    private String accessKey = "";

    /**
     * Secret Key（创建凭证弹窗里的第二条）；新版 Workspace 若列表只有一行 KEY，可留空，等价于 Basic 密码为空。
     */
    private String secretKey = "";

    /** 仅保留项目名称包含该子串的任务（空字符串表示不过滤）。 */
    private String projectNameContains = "";

    /** 进行中任务列表默认 page_size */
    private int activeTasksPageSize = 50;

    @PostConstruct
    public void mergeFromEnvClearmlLocalFile() {
        Path p = ClearmlEnvFileSupport.findEnvClearmlLocal();
        if (p == null || !Files.isRegularFile(p)) {
            log.debug("ClearML env file not found (clearml.env.file / backend/env.clearml.local)");
            return;
        }
        try {
            Map<String, Object> m = ClearmlEnvFileSupport.parseDotEnv(p);
            applyFlatClearmlEnv(m);
            log.info("Merged sys.clearml from env file: {}", p.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed reading ClearML env file {}: {}", p.toAbsolutePath(), e.getMessage());
        }
    }

    private void applyFlatClearmlEnv(Map<String, Object> m) {
        Object en = m.get("CLEARML_ENABLED");
        if (en != null) {
            String s = String.valueOf(en).trim();
            if (!s.isEmpty()) {
                this.enabled = Boolean.parseBoolean(s);
            }
        }
        applyString(m, "CLEARML_API_HOST", v -> this.apiHost = v);
        applyString(m, "CLEARML_WEB_HOST", v -> this.webHost = v);
        applyString(m, "CLEARML_API_VERSION", v -> this.apiVersion = v);
        applyString(m, "CLEARML_PROJECT_NAME_CONTAINS", v -> this.projectNameContains = v);

        Object ps = m.get("CLEARML_ACTIVE_TASKS_PAGE_SIZE");
        if (ps != null) {
            String s = String.valueOf(ps).trim();
            if (StrUtil.isNotBlank(s)) {
                try {
                    this.activeTasksPageSize = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        applyString(m, "CLEARML_API_ACCESS_KEY", v -> this.accessKey = v);
        applyString(m, "CLEARML_API_SECRET_KEY", v -> this.secretKey = v);
    }

    private static void applyString(Map<String, Object> m, String key, java.util.function.Consumer<String> setter) {
        Object v = m.get(key);
        if (v == null) {
            return;
        }
        String s = String.valueOf(v).trim();
        if (!s.isEmpty()) {
            setter.accept(s);
        }
    }
}
