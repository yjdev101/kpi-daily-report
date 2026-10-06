package com.kpireport.report;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kpireport.KpiReportProperties;
import com.kpireport.sender.ReportSender;
import com.kpireport.snapshot.KpiSnapshotRepository;
import com.kpireport.template.KpiTemplate;
import com.kpireport.template.KpiTemplateRegistry;

@Service
public class DailyReportService {

    private static final Logger log = LoggerFactory.getLogger(DailyReportService.class);

    private final KpiCalculator calculator;
    private final KpiSnapshotRepository snapshots;
    private final ReportRunRepository runs;
    private final KpiTemplateRegistry registry;
    private final List<ReportSender> senders;
    private final KpiReportProperties properties;
    private final Clock clock;

    public DailyReportService(KpiCalculator calculator, KpiSnapshotRepository snapshots, ReportRunRepository runs,
                              KpiTemplateRegistry registry, List<ReportSender> senders,
                              KpiReportProperties properties, Clock clock) {
        this.calculator = calculator;
        this.snapshots = snapshots;
        this.runs = runs;
        this.registry = registry;
        this.senders = senders;
        this.properties = properties;
        this.clock = clock;
    }

    public LocalDate yesterday() {
        return LocalDate.now(clock).minusDays(1);
    }

    /**
     * 그날 KPI를 계산해 저장하고, send가 true면 리포트를 보낸다.
     * 전송이 실패해도 계산 결과는 남기고 실행 기록에 SEND_FAILED로 표시한다.
     */
    public DailyReport run(LocalDate date, TriggerType trigger, boolean send) {
        requireCompletedDay(date);
        long runId = runs.start(date, trigger, now());
        try {
            Map<String, BigDecimal> values = calculator.calculate(date);
            snapshots.replace(date, values, now());
            DailyReport report = build(date, values);

            List<String> failures = send ? sendAll(report) : List.of();
            if (failures.isEmpty()) {
                runs.finish(runId, RunStatus.SUCCESS, null, now());
            } else {
                runs.finish(runId, RunStatus.SEND_FAILED, String.join("; ", failures), now());
            }
            return report;
        } catch (RuntimeException e) {
            runs.finish(runId, RunStatus.FAILED, e.getMessage(), now());
            throw e;
        }
    }

    /** 지난 날짜를 다시 계산한다 (보내지는 않음). 템플릿을 추가·수정한 뒤 과거 값을 채울 때 쓴다. */
    public int backfill(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작일이 종료일보다 늦습니다");
        }
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        if (days > properties.backfillMaxDays()) {
            throw new IllegalArgumentException("한 번에 최대 " + properties.backfillMaxDays() + "일까지 다시 계산할 수 있습니다");
        }
        requireCompletedDay(to);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            run(d, TriggerType.BACKFILL, false);
        }
        return (int) days;
    }

    /** 저장된 값으로 리포트를 다시 만든다. 계산한 적 없는 날짜면 null. */
    public DailyReport find(LocalDate date) {
        Map<String, BigDecimal> values = snapshots.findByDate(date);
        return values.isEmpty() ? null : build(date, values);
    }

    private DailyReport build(LocalDate date, Map<String, BigDecimal> values) {
        Map<String, BigDecimal> previousDay = snapshots.findByDate(date.minusDays(1));
        Map<String, BigDecimal> previousWeek = snapshots.findByDate(date.minusWeeks(1));
        List<DailyReport.Line> lines = new ArrayList<>();
        for (KpiTemplate t : registry.all()) {
            if (values.containsKey(t.code())) {
                lines.add(new DailyReport.Line(t.code(), t.name(), t.unit(), values.get(t.code()),
                        previousDay.get(t.code()), previousWeek.get(t.code())));
            }
        }
        return new DailyReport(date, lines);
    }

    private List<String> sendAll(DailyReport report) {
        List<String> failures = new ArrayList<>();
        for (ReportSender sender : senders) {
            try {
                sender.send(report);
            } catch (RuntimeException e) {
                log.warn("리포트 전송 실패: sender={}, date={}", sender.name(), report.date(), e);
                failures.add(sender.name() + ": " + e.getMessage());
            }
        }
        return failures;
    }

    // 오늘은 아직 끝나지 않아 값이 계속 바뀌므로 어제까지만 계산한다
    private void requireCompletedDay(LocalDate date) {
        if (date.isAfter(yesterday())) {
            throw new IllegalArgumentException("아직 끝나지 않은 날짜입니다: " + date);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
