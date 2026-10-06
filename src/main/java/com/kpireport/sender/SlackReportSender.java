package com.kpireport.sender;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.kpireport.report.DailyReport;

/** Slack Incoming Webhook으로 보낸다. kpi.slack.webhook-url을 설정했을 때만 켜진다. */
@Component
@ConditionalOnProperty(name = "kpi.slack.webhook-url")
public class SlackReportSender implements ReportSender {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackReportSender(RestClient.Builder builder, @Value("${kpi.slack.webhook-url}") String webhookUrl) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public String name() {
        return "slack";
    }

    @Override
    public void send(DailyReport report) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", ReportFormatter.toText(report)))
                .retrieve()
                .toBodilessEntity();
    }
}
