-- code: arppu
-- name: 결제자 1인당 매출(ARPPU)
-- unit: 원
SELECT COALESCE(SUM(amount) / NULLIF(COUNT(DISTINCT member_id), 0), 0)
FROM purchase
WHERE status = 'PAID' AND paid_at >= :from AND paid_at < :to
