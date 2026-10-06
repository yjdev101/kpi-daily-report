package com.kpireport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param zone            리포트 날짜를 자르는 기준 시간대
 * @param templateLocation KPI SQL 템플릿 위치
 * @param backfillMaxDays 한 번에 다시 계산할 수 있는 최대 일수
 */
@ConfigurationProperties(prefix = "kpi")
public record KpiReportProperties(
        @DefaultValue("Asia/Seoul") String zone,
        @DefaultValue("classpath:kpi/*.sql") String templateLocation,
        @DefaultValue("93") int backfillMaxDays
) {
}
