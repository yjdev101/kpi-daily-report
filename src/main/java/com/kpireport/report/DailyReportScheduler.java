package com.kpireport.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 아침 어제 리포트를 만들어 보낸다. kpi.schedule.enabled=false면 끈다 (테스트·수동 운영용). */
@Component
@ConditionalOnProperty(name = "kpi.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class DailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyReportScheduler.class);

    private final DailyReportService service;

    public DailyReportScheduler(DailyReportService service) {
        this.service = service;
    }

    @Scheduled(cron = "${kpi.schedule.cron:0 0 7 * * *}", zone = "${kpi.zone:Asia/Seoul}")
    public void runYesterday() {
        try {
            DailyReport report = service.run(service.yesterday(), TriggerType.SCHEDULED, true);
            log.info("일일 리포트 완료: date={}, kpis={}", report.date(), report.lines().size());
        } catch (RuntimeException e) {
            // 실패는 report_run에 남아 있다. 스케줄러 스레드가 죽지 않도록 여기서 끝낸다
            log.error("일일 리포트 실패", e);
        }
    }
}
