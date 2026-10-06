package com.kpireport.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kpireport.report.DailyReport;
import com.kpireport.report.DailyReportService;
import com.kpireport.report.ReportRunRepository;
import com.kpireport.report.TriggerType;
import com.kpireport.snapshot.KpiSnapshotRepository;
import com.kpireport.template.KpiTemplate;
import com.kpireport.template.KpiTemplateRegistry;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final DailyReportService service;
    private final KpiSnapshotRepository snapshots;
    private final ReportRunRepository runs;
    private final KpiTemplateRegistry registry;

    public ReportController(DailyReportService service, KpiSnapshotRepository snapshots,
                            ReportRunRepository runs, KpiTemplateRegistry registry) {
        this.service = service;
        this.snapshots = snapshots;
        this.runs = runs;
        this.registry = registry;
    }

    @GetMapping("/kpis")
    public List<KpiTemplate> kpis() {
        return registry.all();
    }

    @GetMapping("/reports/{date}")
    public ResponseEntity<DailyReport> report(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailyReport report = service.find(date);
        return report == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(report);
    }

    /** 수동 실행. 기본은 계산만 하고, send=true면 설정된 곳으로 보낸다. */
    @PostMapping("/reports/{date}/run")
    public DailyReport run(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(defaultValue = "false") boolean send) {
        return service.run(date, TriggerType.MANUAL, send);
    }

    @PostMapping("/reports/backfill")
    public Map<String, Integer> backfill(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Map.of("days", service.backfill(from, to));
    }

    @GetMapping("/kpis/{code}/trend")
    public ResponseEntity<List<KpiSnapshotRepository.KpiPoint>> trend(
            @PathVariable String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (registry.find(code).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(snapshots.findTrend(code, from, to));
    }

    @GetMapping("/runs")
    public List<ReportRunRepository.ReportRun> runs(@RequestParam(defaultValue = "20") int limit) {
        return runs.findRecent(Math.min(Math.max(limit, 1), 100));
    }
}
