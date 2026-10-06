package com.kpireport.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 리포트 날짜 하루를 [from, to) 구간으로 바꾼다. MAU는 그날 포함 최근 30일.
 * 원천 데이터의 시각은 서비스 기준 시간대(kpi.zone)의 로컬 시각으로 저장돼 있다고 가정한다.
 */
public record ReportPeriod(LocalDate date) {

    static final int MAU_DAYS = 30;

    public LocalDateTime from() {
        return date.atStartOfDay();
    }

    public LocalDateTime to() {
        return date.plusDays(1).atStartOfDay();
    }

    public LocalDateTime mauFrom() {
        return date.minusDays(MAU_DAYS - 1).atStartOfDay();
    }

    public Map<String, Object> parameters() {
        return Map.of("from", from(), "to", to(), "mauFrom", mauFrom());
    }
}
