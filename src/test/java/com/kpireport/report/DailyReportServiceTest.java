package com.kpireport.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.kpireport.sender.ReportSender;

/** H2(PostgreSQL 모드)에 원천 데이터를 직접 넣고 KPI 값이 정확한지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
class DailyReportServiceTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDate D = LocalDate.of(2026, 3, 10);   // 리포트 날짜 (화요일)

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(ZonedDateTime.of(D.plusDays(1).atTime(7, 0), SEOUL).toInstant(), SEOUL);
        }

        @Bean
        FailingSender failingSender() {
            return new FailingSender();
        }
    }

    static class FailingSender implements ReportSender {
        @Override
        public String name() {
            return "failing";
        }

        @Override
        public void send(DailyReport report) {
            throw new IllegalStateException("webhook 404");
        }
    }

    @Autowired
    DailyReportService service;

    @Autowired
    JdbcTemplate jdbc;

    long m1;
    long m2;
    long m3;

    @BeforeEach
    void setUp() {
        for (String table : new String[] {"kpi_snapshot", "report_run", "material_download", "purchase", "login_event", "member"}) {
            jdbc.update("DELETE FROM " + table);
        }
        m1 = member(D.minusDays(40).atTime(9, 0));
        m2 = member(D.atTime(10, 0));            // 오늘 가입
        m3 = member(D.minusDays(5).atTime(9, 0));

        login(m1, D.atTime(8, 0));
        login(m1, D.atTime(21, 0));              // 같은 사람 두 번 → DAU 1명
        login(m2, D.atTime(10, 5));
        login(m3, D.minusDays(10).atTime(12, 0)); // 최근 30일 안 → MAU에만
        login(m1, D.minusDays(30).atTime(12, 0)); // 30일 밖 (MAU 구간은 D-29부터)
        login(m3, D.plusDays(1).atStartOfDay());  // 다음 날 00:00 → 포함 안 됨

        purchase(m1, 3_000, "PAID", D.atTime(9, 0));
        purchase(m1, 2_000, "PAID", D.atTime(9, 30));
        purchase(m2, 5_000, "PAID", D.atTime(11, 0));
        purchase(m2, 10_000, "REFUNDED", D.atTime(12, 0));
        purchase(m3, 5_000, "PAID", D.minusDays(1).atTime(12, 0));

        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO material_download (member_id, downloaded_at) VALUES (?, ?)", m1, ts(D.atTime(13, i)));
        }
    }

    @Test
    void KPI_값을_정확히_계산한다() {
        DailyReport report = service.run(D, TriggerType.MANUAL, false);

        Map<String, BigDecimal> v = report.lines().stream()
                .collect(Collectors.toMap(DailyReport.Line::code, DailyReport.Line::value));
        assertThat(v.get("dau")).isEqualByComparingTo("2");
        assertThat(v.get("mau")).isEqualByComparingTo("3");
        assertThat(v.get("new_members")).isEqualByComparingTo("1");
        assertThat(v.get("revenue")).isEqualByComparingTo("10000");   // 환불 제외
        assertThat(v.get("paying_members")).isEqualByComparingTo("2");
        assertThat(v.get("arppu")).isEqualByComparingTo("5000");
        assertThat(v.get("downloads")).isEqualByComparingTo("3");
        assertThat(v.get("dau_mau_ratio")).isEqualByComparingTo("66.67");
    }

    @Test
    void 같은_날짜를_다시_돌려도_스냅샷은_한_벌만_남는다() {
        service.run(D, TriggerType.MANUAL, false);
        service.run(D, TriggerType.MANUAL, false);

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM kpi_snapshot WHERE report_date = ?", Integer.class, D);
        assertThat(rows).isEqualTo(8);
    }

    @Test
    void 전일_값과_비교한_변화율을_붙인다() {
        service.run(D.minusDays(1), TriggerType.BACKFILL, false);
        DailyReport report = service.run(D, TriggerType.MANUAL, false);

        DailyReport.Line revenue = line(report, "revenue");
        assertThat(revenue.previousDay()).isEqualByComparingTo("5000");
        assertThat(revenue.dayOverDayPercent()).isEqualByComparingTo("100.0");
        assertThat(revenue.previousWeek()).isNull();                 // 7일 전은 계산한 적 없음
        assertThat(line(report, "dau").dayOverDayPercent()).isNull(); // 전일 DAU 0 → 변화율 없음
    }

    @Test
    void 전송이_실패해도_계산_결과는_저장하고_SEND_FAILED로_남긴다() {
        service.run(D, TriggerType.SCHEDULED, true);

        assertThat(service.find(D)).isNotNull();
        Map<String, Object> run = jdbc.queryForMap("SELECT status, message FROM report_run");
        assertThat(run.get("status")).isEqualTo("SEND_FAILED");
        assertThat((String) run.get("message")).contains("failing").contains("webhook 404");
    }

    @Test
    void 끝나지_않은_오늘은_계산하지_않는다() {
        assertThatThrownBy(() -> service.run(D.plusDays(1), TriggerType.MANUAL, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 백필은_기간_안의_날짜를_모두_계산한다() {
        int days = service.backfill(D.minusDays(7), D);   // 전주 같은 요일까지 포함

        assertThat(days).isEqualTo(8);
        Integer dates = jdbc.queryForObject("SELECT COUNT(DISTINCT report_date) FROM kpi_snapshot", Integer.class);
        assertThat(dates).isEqualTo(8);
        assertThat(line(service.find(D), "revenue").previousWeek()).isNotNull();
    }

    @Test
    void 백필_기간이_너무_길면_거절한다() {
        assertThatThrownBy(() -> service.backfill(D.minusDays(200), D))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DailyReport.Line line(DailyReport report, String code) {
        return report.lines().stream().filter(l -> l.code().equals(code)).findFirst().orElseThrow();
    }

    private long member(LocalDateTime joinedAt) {
        jdbc.update("INSERT INTO member (joined_at) VALUES (?)", ts(joinedAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM member", Long.class);
    }

    private void login(long memberId, LocalDateTime at) {
        jdbc.update("INSERT INTO login_event (member_id, occurred_at) VALUES (?, ?)", memberId, ts(at));
    }

    private void purchase(long memberId, int amount, String status, LocalDateTime at) {
        jdbc.update("INSERT INTO purchase (member_id, amount, status, paid_at) VALUES (?, ?, ?, ?)",
                memberId, amount, status, ts(at));
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
