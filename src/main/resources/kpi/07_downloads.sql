-- code: downloads
-- name: 자료 다운로드
-- unit: 건
SELECT COUNT(*)
FROM material_download
WHERE downloaded_at >= :from AND downloaded_at < :to
