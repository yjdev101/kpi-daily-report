-- code: revenue
-- name: 매출(환불 제외)
-- unit: 원
SELECT COALESCE(SUM(amount), 0)
FROM purchase
WHERE status = 'PAID' AND paid_at >= :from AND paid_at < :to
