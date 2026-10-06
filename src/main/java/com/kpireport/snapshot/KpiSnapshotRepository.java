package com.kpireport.snapshot;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class KpiSnapshotRepository {

    private final JdbcTemplate jdbc;

    public KpiSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 같은 날짜를 다시 계산하면 이전 값을 지우고 새로 넣는다 (재실행·백필해도 결과가 하나만 남음). */
    @Transactional
    public void replace(LocalDate date, Map<String, BigDecimal> values, LocalDateTime computedAt) {
        jdbc.update("DELETE FROM kpi_snapshot WHERE report_date = ?", Date.valueOf(date));
        List<Object[]> rows = values.entrySet().stream()
                .map(e -> new Object[] {Date.valueOf(date), e.getKey(), e.getValue(), Timestamp.valueOf(computedAt)})
                .toList();
        jdbc.batchUpdate("INSERT INTO kpi_snapshot (report_date, kpi_code, kpi_value, computed_at) VALUES (?, ?, ?, ?)", rows);
    }

    public Map<String, BigDecimal> findByDate(LocalDate date) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        jdbc.query("SELECT kpi_code, kpi_value FROM kpi_snapshot WHERE report_date = ?",
                rs -> {
                    values.put(rs.getString("kpi_code"), rs.getBigDecimal("kpi_value"));
                },
                Date.valueOf(date));
        return values;
    }

    public List<KpiPoint> findTrend(String code, LocalDate from, LocalDate to) {
        return jdbc.query("""
                        SELECT report_date, kpi_value FROM kpi_snapshot
                        WHERE kpi_code = ? AND report_date BETWEEN ? AND ?
                        ORDER BY report_date
                        """,
                (rs, i) -> new KpiPoint(rs.getDate("report_date").toLocalDate(), rs.getBigDecimal("kpi_value")),
                code, Date.valueOf(from), Date.valueOf(to));
    }

    public record KpiPoint(LocalDate date, BigDecimal value) {
    }
}
