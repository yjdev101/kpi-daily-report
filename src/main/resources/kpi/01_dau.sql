-- code: dau
-- name: 일간 활성 사용자(DAU)
-- unit: 명
SELECT COUNT(DISTINCT member_id)
FROM login_event
WHERE occurred_at >= :from AND occurred_at < :to
