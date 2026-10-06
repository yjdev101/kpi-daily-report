package com.kpireport.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class KpiTemplateRegistryTest {

    @Test
    void 헤더와_SQL을_읽고_파일명_숫자로_순서를_정한다() {
        KpiTemplate t = KpiTemplateRegistry.parse("03_x.sql", """
                -- code: new_members
                -- name: 신규 가입
                -- unit: 명
                SELECT COUNT(*)
                FROM member
                WHERE joined_at >= :from AND joined_at < :to
                """);

        assertThat(t.code()).isEqualTo("new_members");
        assertThat(t.name()).isEqualTo("신규 가입");
        assertThat(t.unit()).isEqualTo("명");
        assertThat(t.order()).isEqualTo(3);
        assertThat(t.sql()).startsWith("SELECT COUNT(*)").doesNotContain("--");
    }

    @Test
    void code_헤더가_없으면_실패() {
        assertThatThrownBy(() -> KpiTemplateRegistry.parse("x.sql", "-- name: a\n-- unit: 명\nSELECT 1"))
                .hasMessageContaining("code");
    }

    @Test
    void 데이터를_바꾸는_문장은_거부() {
        assertThatThrownBy(() -> KpiTemplateRegistry.parse("x.sql",
                "-- code: x\n-- name: a\n-- unit: 명\nSELECT 1 FROM (DELETE FROM member RETURNING id) t"))
                .hasMessageContaining("바꾸는");
    }

    @Test
    void 여러_문장은_거부() {
        assertThatThrownBy(() -> KpiTemplateRegistry.parse("x.sql",
                "-- code: x\n-- name: a\n-- unit: 명\nSELECT 1; SELECT 2"))
                .hasMessageContaining("SELECT 문 하나");
    }

    @Test
    void updated_at_같은_컬럼명은_키워드로_보지_않는다() {
        KpiTemplate t = KpiTemplateRegistry.parse("x.sql",
                "-- code: x\n-- name: a\n-- unit: 명\nSELECT COUNT(*) FROM member WHERE updated_at IS NOT NULL");
        assertThat(t.code()).isEqualTo("x");
    }

    @Test
    void 프로젝트의_템플릿_8개를_순서대로_불러온다() {
        Map<String, KpiTemplate> all = KpiTemplateRegistry.load("classpath:kpi/*.sql");

        assertThat(all.keySet()).containsExactly(
                "dau", "mau", "new_members", "revenue", "paying_members", "arppu", "downloads", "dau_mau_ratio");
    }
}
