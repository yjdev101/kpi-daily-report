package com.kpireport.template;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.kpireport.KpiReportProperties;

/**
 * kpi/*.sql 파일을 읽어 KPI 목록을 만든다.
 * 새 KPI는 코드 수정 없이 SQL 파일 하나를 추가하면 된다. 잘못된 템플릿은 앱 시작 시점에 바로 실패시킨다.
 */
@Component
public class KpiTemplateRegistry {

    private static final Pattern HEADER = Pattern.compile("^--\\s*(code|name|unit):\\s*(.+)$");
    private static final Pattern FILE_ORDER = Pattern.compile("^(\\d+)_.*\\.sql$");
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,49}$");
    private static final Pattern WRITE_KEYWORD =
            Pattern.compile("\\b(INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|GRANT)\\b", Pattern.CASE_INSENSITIVE);

    private final Map<String, KpiTemplate> templates;

    public KpiTemplateRegistry(KpiReportProperties properties) {
        this.templates = load(properties.templateLocation());
    }

    public List<KpiTemplate> all() {
        return List.copyOf(templates.values());
    }

    public Optional<KpiTemplate> find(String code) {
        return Optional.ofNullable(templates.get(code));
    }

    static Map<String, KpiTemplate> load(String location) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(location);
            List<KpiTemplate> parsed = new ArrayList<>();
            for (Resource resource : resources) {
                parsed.add(parse(resource.getFilename(), resource.getContentAsString(StandardCharsets.UTF_8)));
            }
            if (parsed.isEmpty()) {
                throw new IllegalStateException("KPI 템플릿이 없습니다: " + location);
            }
            parsed.sort(Comparator.comparingInt(KpiTemplate::order).thenComparing(KpiTemplate::code));

            Map<String, KpiTemplate> byCode = new LinkedHashMap<>();
            for (KpiTemplate template : parsed) {
                if (byCode.putIfAbsent(template.code(), template) != null) {
                    throw new IllegalStateException("KPI 코드가 중복됩니다: " + template.code());
                }
            }
            return byCode;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static KpiTemplate parse(String filename, String content) {
        Map<String, String> header = new HashMap<>();
        StringBuilder sql = new StringBuilder();
        for (String line : content.split("\\R")) {
            Matcher m = HEADER.matcher(line.strip());
            if (m.matches()) {
                header.put(m.group(1), m.group(2).strip());
            } else if (!line.strip().startsWith("--") && !line.isBlank()) {
                sql.append(line).append('\n');
            }
        }

        String code = header.get("code");
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalStateException(filename + ": '-- code:'는 소문자·숫자·밑줄로 적어야 합니다");
        }
        for (String required : List.of("name", "unit")) {
            if (!header.containsKey(required)) {
                throw new IllegalStateException(filename + ": '-- " + required + ":' 헤더가 없습니다");
            }
        }
        String body = sql.toString().strip();
        if (!body.regionMatches(true, 0, "SELECT", 0, 6) || body.contains(";")) {
            throw new IllegalStateException(filename + ": SELECT 문 하나만 쓸 수 있습니다");
        }
        if (WRITE_KEYWORD.matcher(body).find()) {
            throw new IllegalStateException(filename + ": 데이터를 바꾸는 문장은 쓸 수 없습니다");
        }

        Matcher order = FILE_ORDER.matcher(filename);
        int sortOrder = order.matches() ? Integer.parseInt(order.group(1)) : Integer.MAX_VALUE;
        return new KpiTemplate(code, header.get("name"), header.get("unit"), sortOrder, body);
    }
}
