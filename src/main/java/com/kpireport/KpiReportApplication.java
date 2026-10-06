package com.kpireport;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class KpiReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(KpiReportApplication.class, args);
    }

    // 리포트 기준 날짜(어제)를 테스트에서 고정할 수 있도록 Clock을 빈으로 둔다
    @Bean
    Clock clock(KpiReportProperties properties) {
        return Clock.system(ZoneId.of(properties.zone()));
    }
}
