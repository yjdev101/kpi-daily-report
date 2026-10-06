package com.kpireport.template;

/**
 * SQL 파일 하나 = KPI 하나.
 * 쿼리는 숫자 하나를 돌려줘야 하며 :from, :to, :mauFrom 파라미터를 쓸 수 있다.
 */
public record KpiTemplate(String code, String name, String unit, int order, String sql) {
}
