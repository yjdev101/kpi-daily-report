package com.kpireport.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kpireport.report.DailyReport;

/** 항상 켜져 있는 기본 전송처. 다른 전송처를 설정하지 않아도 로그로 결과를 확인할 수 있다. */
@Component
public class LogReportSender implements ReportSender {

    private static final Logger log = LoggerFactory.getLogger(LogReportSender.class);

    @Override
    public String name() {
        return "log";
    }

    @Override
    public void send(DailyReport report) {
        log.info("\n{}", ReportFormatter.toText(report));
    }
}
