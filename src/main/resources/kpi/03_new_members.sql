-- code: new_members
-- name: 신규 가입
-- unit: 명
SELECT COUNT(*)
FROM member
WHERE joined_at >= :from AND joined_at < :to
