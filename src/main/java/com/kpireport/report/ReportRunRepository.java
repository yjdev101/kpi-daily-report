package com.kpireport.report;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** 리포트 실행 기록. 언제 누가(스케줄/수동) 돌렸고 성공했는지 남긴다. */
@Repository
public class ReportRunRepository {

    private final JdbcTemplate jdbc;

    public ReportRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long start(LocalDate date, TriggerType trigger, LocalDateTime now) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO report_run (report_date, trigger_type, status, started_at) VALUES (?, ?, ?, ?)",
                    new String[] {"id"});
            ps.setDate(1, Date.valueOf(date));
            ps.setString(2, trigger.name());
            ps.setString(3, RunStatus.RUNNING.name());
            ps.setTimestamp(4, Timestamp.valueOf(now));
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    public void finish(long id, RunStatus status, String message, LocalDateTime now) {
        String trimmed = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
        jdbc.update("UPDATE report_run SET status = ?, message = ?, finished_at = ? WHERE id = ?",
                status.name(), trimmed, Timestamp.valueOf(now), id);
    }

    public List<ReportRun> findRecent(int limit) {
        return jdbc.query("""
                        SELECT id, report_date, trigger_type, status, message, started_at, finished_at
                        FROM report_run ORDER BY id DESC LIMIT ?
                        """,
                (rs, i) -> new ReportRun(
                        rs.getLong("id"),
                        rs.getDate("report_date").toLocalDate(),
                        TriggerType.valueOf(rs.getString("trigger_type")),
                        RunStatus.valueOf(rs.getString("status")),
                        rs.getString("message"),
                        rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime()),
                limit);
    }

    public record ReportRun(long id, LocalDate reportDate, TriggerType trigger, RunStatus status, String message,
                            LocalDateTime startedAt, LocalDateTime finishedAt) {
    }
}
