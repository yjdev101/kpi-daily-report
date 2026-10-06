-- code: paying_members
-- name: 결제자 수
-- unit: 명
SELECT COUNT(DISTINCT member_id)
FROM purchase
WHERE status = 'PAID' AND paid_at >= :from AND paid_at < :to
