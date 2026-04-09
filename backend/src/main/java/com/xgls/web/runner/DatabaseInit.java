package com.xgls.web.runner;

import java.util.Arrays;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xgls.web.config.DatabaseChecker;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Order(1)
public class DatabaseInit implements ApplicationRunner {

    /* ---------- App 级开关（自定义） ---------- */

    /** 是否启用 DatabaseInit 中的 Flyway 逻辑；设为 false 相当于完全不用 Flyway */
    @Value("${app.flyway.enabled:true}")
    private boolean appFlywayEnabled;

    /** 运行模式：migrate | repair | disable */
    @Value("${app.flyway.mode:migrate}")
    private String mode;

    /** 允许乱序执行（当 48..52 这类版本晚于 53 才被发现时需要） */
    @Value("${app.flyway.out-of-order:false}")
    private boolean outOfOrder;

    /** 迁移脚本位置，多个用逗号分隔 */
    @Value("${app.flyway.locations:classpath:db/migration}")
    private String locations;

    /* ---------- 复用 spring.flyway 的常见配置 ---------- */

    @Value("${spring.flyway.clean-disabled:true}")
    private boolean cleanDisabled;

    @Value("${spring.flyway.mixed:true}")
    private boolean mixed;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.baseline-version:0}")
    private String baselineVersion;

    @Autowired
    private DatabaseChecker databaseChecker;

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 等待外部依赖（你原有逻辑）
        while (!databaseChecker.isMySqlConnected() || !databaseChecker.isRedisConnected()) {
            log.warn("waiting for mysql and redis ~~~~~~~~~~~~~~~~~~~");
            Thread.sleep(3000);
        }

        // 总开关：禁用则直接跳过（等同不用 Flyway）
        if (!appFlywayEnabled || "disable".equalsIgnoreCase(mode)) {
            log.info("Flyway is disabled by configuration (app.flyway.enabled={}, mode={}). Skip Flyway.", appFlywayEnabled, mode);
            return;
        }

        initFlyway();
    }

    /** 初始化并按模式执行 Flyway */
    private void initFlyway() {
        String[] locs = Arrays.stream(locations.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        log.info("manual init flyway ~~~ mode={}, outOfOrder={}, locations={}", mode, outOfOrder, Arrays.toString(locs));

        try {
            var fluent = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locs)
                    .cleanDisabled(cleanDisabled)
                    .mixed(mixed)
                    .baselineOnMigrate(baselineOnMigrate)
                    .baselineVersion(baselineVersion);

            if (outOfOrder) {
                fluent.outOfOrder(true);
            }

            Flyway flyway = fluent.load();

            switch (mode.toLowerCase()) {
                case "repair":
                    log.warn("Running Flyway REPAIR (开发/测试环境使用；生产谨慎)...");
                    flyway.repair();
                    log.info("Flyway repair done.");
                    break;
                case "migrate":
                    log.info("Running Flyway MIGRATE...");
                    flyway.migrate();
                    log.info("Flyway migrate done.");
                    break;
                default:
                    log.warn("Unknown app.flyway.mode={}, skip Flyway.", mode);
            }
        } catch (FlywayException ex) {
            log.error("Flyway operation failed. mode={}, outOfOrder={}, locations={}", mode, outOfOrder, Arrays.toString(locs), ex);
            // 失败时让应用按你原本的行为退出
            throw ex;
        }
    }
}
