package com.kpireport.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** 하루치 리포트. 값마다 전일·전주 같은 요일과 비교한 변화율을 같이 담는다. */
public record DailyReport(LocalDate date, List<Line> lines) {

    /**
     * @param previousDay  전일 값 (스냅샷이 없으면 null)
     * @param previousWeek 7일 전 값 (스냅샷이 없으면 null)
     */
    public record Line(String code, String name, String unit, BigDecimal value,
                       BigDecimal previousDay, BigDecimal previousWeek) {

        public BigDecimal dayOverDayPercent() {
            return changePercent(value, previousDay);
        }

        public BigDecimal weekOverWeekPercent() {
            return changePercent(value, previousWeek);
        }
    }

    /** 비교 대상이 없거나 0이면 변화율을 계산하지 않는다 (null). */
    static BigDecimal changePercent(BigDecimal current, BigDecimal base) {
        if (current == null || base == null || base.signum() == 0) {
            return null;
        }
        return current.subtract(base)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 1, RoundingMode.HALF_UP);
    }
}
