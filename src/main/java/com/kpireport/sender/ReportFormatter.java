package com.kpireport.sender;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import com.kpireport.report.DailyReport;

/** 메신저에 붙여 읽기 좋은 텍스트로 만든다. */
public final class ReportFormatter {

    private ReportFormatter() {
    }

    public static String toText(DailyReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("[일일 KPI] ").append(report.date()).append('\n');
        for (DailyReport.Line line : report.lines()) {
            sb.append("• ").append(line.name()).append(": ")
                    .append(number(line.value())).append(' ').append(line.unit())
                    .append("  (전일 ").append(change(line.dayOverDayPercent()))
                    .append(", 전주 ").append(change(line.weekOverWeekPercent())).append(")\n");
        }
        return sb.toString();
    }

    static String number(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }

    static String change(BigDecimal percent) {
        if (percent == null) {
            return "-";
        }
        return (percent.signum() > 0 ? "▲" : percent.signum() < 0 ? "▼" : "") + percent.abs().toPlainString() + "%";
    }
}
