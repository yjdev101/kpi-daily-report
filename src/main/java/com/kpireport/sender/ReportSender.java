package com.kpireport.sender;

import com.kpireport.report.DailyReport;

/** 리포트를 받는 곳. 메일·메신저 등을 추가하려면 이 인터페이스를 구현한 빈을 하나 더 만든다. */
public interface ReportSender {

    String name();

    void send(DailyReport report);
}
