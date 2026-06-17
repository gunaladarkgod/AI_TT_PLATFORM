package com.xgls.web.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 在未手动 export 的情况下，自动加载仓库内 {@code env.clearml.local}（KEY=value），使 CLEARML_* 注入 JVM，
 * 避免出现 sys.clearml.enabled=false。
 */
public class ClearmlDotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ClearmlDotEnvEnvironmentPostProcessor.class);

    static final String PROPERTY_SOURCE_NAME = "clearmlEnvLocalFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path loaded = ClearmlEnvFileSupport.findEnvClearmlLocal();
        if (loaded == null || !Files.isRegularFile(loaded)) {
            return;
        }
        try {
            Map<String, Object> map = ClearmlEnvFileSupport.parseDotEnv(loaded);
            if (!map.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
                log.info("Loaded ClearML env file into Spring Environment: {}", loaded.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading ClearML env file: " + loaded, e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
