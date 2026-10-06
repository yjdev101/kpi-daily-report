-- code: dau_mau_ratio
-- name: 고착도(DAU/MAU)
-- unit: %
SELECT COALESCE(
    100.0 * (SELECT COUNT(DISTINCT member_id) FROM login_event WHERE occurred_at >= :from AND occurred_at < :to)
    / NULLIF((SELECT COUNT(DISTINCT member_id) FROM login_event WHERE occurred_at >= :mauFrom AND occurred_at < :to), 0),
    0)
