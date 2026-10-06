package com.kpireport.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.kpireport.template.KpiTemplate;
import com.kpireport.template.KpiTemplateRegistry;

/** 템플릿마다 쿼리를 실행해 그날의 KPI 값을 구한다. */
@Component
public class KpiCalculator {

    private final NamedParameterJdbcTemplate jdbc;
    private final KpiTemplateRegistry registry;

    public KpiCalculator(NamedParameterJdbcTemplate jdbc, KpiTemplateRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    public Map<String, BigDecimal> calculate(LocalDate date) {
        Map<String, Object> params = new ReportPeriod(date).parameters();
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (KpiTemplate template : registry.all()) {
            values.put(template.code(), run(template, params));
        }
        return values;
    }

    private BigDecimal run(KpiTemplate template, Map<String, Object> params) {
        try {
            BigDecimal value = jdbc.queryForObject(template.sql(), params, BigDecimal.class);
            return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            throw new KpiQueryException(template.code(), e);
        }
    }
}
