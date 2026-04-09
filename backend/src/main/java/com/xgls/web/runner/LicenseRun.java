package com.xgls.web.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xgls.web.license.LicenseUtil;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
// @Order(0)
public class LicenseRun implements ApplicationRunner {
    /** 开启与否 */
    private static boolean enable = false;
    @Value("${sys.license:''}")
    private String licensePath;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (enable) {
            if (LicenseUtil.checkLiscense(licensePath)) {
                log.info("{}", "^_^~~~~~~License verification succeeded. Starting application...");
            } else {
                log.error("{}", "-_-!~~~~~~~~~License verification failed...");
            }
        }
    }
}
