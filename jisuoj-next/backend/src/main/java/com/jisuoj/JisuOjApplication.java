package com.jisuoj;

import com.jisuoj.config.JisuOjProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(JisuOjProperties.class)
public class JisuOjApplication {
    public static void main(String[] args) {
        SpringApplication.run(JisuOjApplication.class, args);
    }
}
