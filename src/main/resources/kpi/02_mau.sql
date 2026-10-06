-- code: mau
-- name: 월간 활성 사용자(MAU, 최근 30일)
-- unit: 명
SELECT COUNT(DISTINCT member_id)
FROM login_event
WHERE occurred_at >= :mauFrom AND occurred_at < :to
