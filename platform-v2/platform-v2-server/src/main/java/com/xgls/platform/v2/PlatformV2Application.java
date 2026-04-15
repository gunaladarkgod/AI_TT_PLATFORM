package com.xgls.platform.v2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.xgls.platform.v2.auth.config.PlatformAuthProperties;

@SpringBootApplication
@EnableConfigurationProperties(PlatformAuthProperties.class)
public class PlatformV2Application {

    public static void main(String[] args) {
        SpringApplication.run(PlatformV2Application.class, args);
    }
}
