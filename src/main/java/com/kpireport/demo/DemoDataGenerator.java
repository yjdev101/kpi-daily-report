package com.kpireport.demo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * demo 프로필에서 학습자료 마켓의 가상 데이터를 만든다 (DB가 비어 있을 때만).
 * 시드를 고정해 실행할 때마다 같은 데이터가 나온다. 주말에는 활동이 줄고, 회원은 조금씩 늘어난다.
 */
@Component
@Profile("demo")
public class DemoDataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataGenerator.class);
    private static final int DAYS = 120;
    private static final int[] PRICES = {1_000, 2_000, 3_000, 5_000, 10_000};

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoDataGenerator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
        if (members != null && members > 0) {
            log.info("데모 데이터가 이미 있어 건너뜀 (회원 {}명)", members);
            return;
        }
        Random random = new Random(20101);
        LocalDate start = LocalDate.now(clock).minusDays(DAYS);
        List<Long> memberIds = new ArrayList<>();

        for (int day = 0; day < DAYS; day++) {
            LocalDate date = start.plusDays(day);
            boolean weekend = date.getDayOfWeek().getValue() >= 6;

            int joins = 15 + random.nextInt(15);
            for (int i = 0; i < joins; i++) {
                memberIds.add(insertMember(at(date, random)));
            }

            double activeRate = weekend ? 0.12 : 0.22;
            List<Object[]> logins = new ArrayList<>();
            List<Object[]> downloads = new ArrayList<>();
            List<Object[]> purchases = new ArrayList<>();
            for (Long id : memberIds) {
                if (random.nextDouble() >= activeRate) {
                    continue;
                }
                logins.add(new Object[] {id, at(date, random)});
                for (int d = random.nextInt(4); d > 0; d--) {
                    downloads.add(new Object[] {id, at(date, random)});
                }
                if (random.nextDouble() < 0.08) {
                    String status = random.nextDouble() < 0.05 ? "REFUNDED" : "PAID";
                    BigDecimal amount = BigDecimal.valueOf(PRICES[random.nextInt(PRICES.length)]);
                    purchases.add(new Object[] {id, amount, status, at(date, random)});
                }
            }
            jdbc.batchUpdate("INSERT INTO login_event (member_id, occurred_at) VALUES (?, ?)", logins);
            jdbc.batchUpdate("INSERT INTO material_download (member_id, downloaded_at) VALUES (?, ?)", downloads);
            jdbc.batchUpdate("INSERT INTO purchase (member_id, amount, status, paid_at) VALUES (?, ?, ?, ?)", purchases);
        }
        log.info("데모 데이터 생성: {}일, 회원 {}명", DAYS, memberIds.size());
    }

    private Long insertMember(Timestamp joinedAt) {
        return jdbc.queryForObject("INSERT INTO member (joined_at) VALUES (?) RETURNING id", Long.class, joinedAt);
    }

    private static Timestamp at(LocalDate date, Random random) {
        return Timestamp.valueOf(LocalDateTime.of(date, LocalTime.ofSecondOfDay(random.nextInt(86_400))));
    }
}
