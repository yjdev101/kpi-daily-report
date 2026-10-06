package com.kpireport.sender;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kpireport.report.DailyReport;

class ReportFormatterTest {

    @Test
    void 값과_전일_전주_변화율을_한_줄로_보여준다() {
        DailyReport report = new DailyReport(LocalDate.of(2026, 3, 10), List.of(
                new DailyReport.Line("revenue", "매출", "원", new BigDecimal("1234567"),
                        new BigDecimal("1000000"), null),
                new DailyReport.Line("dau", "DAU", "명", new BigDecimal("90"),
                        new BigDecimal("100"), new BigDecimal("90"))));

        String text = ReportFormatter.toText(report);

        assertThat(text).contains("[일일 KPI] 2026-03-10")
                .contains("• 매출: 1,234,567 원  (전일 ▲23.5%, 전주 -)")
                .contains("• DAU: 90 명  (전일 ▼10.0%, 전주 0.0%)");
    }
}
