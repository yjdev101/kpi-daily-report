package com.kpireport.report;

public class KpiQueryException extends RuntimeException {

    public KpiQueryException(String kpiCode, Throwable cause) {
        super("KPI '" + kpiCode + "' 계산 실패: " + cause.getMessage(), cause);
    }
}
