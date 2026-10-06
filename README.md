# kpi-daily-report

매일 아침 어제의 핵심 KPI(DAU·MAU·매출·결제자·ARPPU 등)를 계산해 저장하고, 전일·전주 대비 변화와 함께 보내 주는 Spring Boot 배치 서비스.

## 왜 만들었나

첫 직장(교육 콘텐츠 서비스)에서 DB를 맡아 **매일 아침 기획실에 전날 KPI를 전달**했다. 처음엔 매번 SQL을 직접 짰고, 나중엔 자주 오는 요청을 상황별 쿼리 템플릿으로 정리해 대응했다. 그래도 매일 사람이 돌리고 붙여 넣어야 했다.

이 프로젝트는 그 일을 지금 다시 한다면 어떻게 만들지에 대한 답이다.

- 쿼리 템플릿 → **SQL 파일 하나 = KPI 하나**. 새 KPI는 코드 수정 없이 파일만 추가
- 매일 손으로 돌리기 → **스케줄러가 07:00에 계산·저장·전송**
- 엑셀에 쌓던 기록 → **스냅샷 테이블**에 날짜별 저장, 전일·전주 비교와 추이 조회
- "어제 숫자 왜 비어 있어요?" → **실행 기록(report_run)** 으로 성공·실패·전송 실패를 구분

## 구조

```
kpi/*.sql (템플릿) ─▶ KpiTemplateRegistry ─▶ KpiCalculator ─▶ kpi_snapshot
                                                    │
DailyReportScheduler (매일 07:00) ─▶ DailyReportService ─▶ ReportSender (로그 / Slack)
                                                    │
                                              report_run (실행 기록)
```

| 패키지 | 역할 |
|---|---|
| `template` | SQL 파일 읽기·검증 (헤더, SELECT 하나만, 데이터 변경 문장 거부). 잘못된 템플릿은 앱 시작 시 실패 |
| `report` | 하루 구간 계산, KPI 실행, 리포트 조립, 스케줄러, 실행 기록 |
| `snapshot` | 날짜별 KPI 값 저장·조회 |
| `sender` | 전송처. `ReportSender`를 구현한 빈을 추가하면 늘어남 |
| `api` | 수동 실행·백필·조회 API |
| `demo` | `demo` 프로필에서 120일치 가상 데이터 생성 |

## 설계에서 고민한 것

- **재실행해도 안전하게(멱등성)**: 같은 날짜를 다시 계산하면 그 날짜 스냅샷을 지우고 새로 넣는다(한 트랜잭션). 템플릿을 고친 뒤 백필해도 값이 중복되지 않는다.
- **날짜 경계**: 하루는 `[00:00, 다음 날 00:00)` 반열린 구간. 자정 정각 이벤트가 이틀에 중복으로 잡히지 않는다. MAU는 그날 포함 최근 30일.
- **오늘은 계산하지 않음**: 끝나지 않은 날은 값이 계속 바뀌므로 어제까지만 허용.
- **전송 실패와 계산 실패 분리**: Slack이 죽어도 계산 결과는 저장하고 `SEND_FAILED`로 남긴다. 다시 보내기만 하면 된다.
- **템플릿 안전장치**: 템플릿은 저장소에 있는 신뢰된 파일이지만, 실수로 데이터를 바꾸는 쿼리가 들어가지 않도록 `SELECT` 하나만 허용하고 변경 키워드를 거부한다. 값은 모두 이름 있는 파라미터(`:from`, `:to`, `:mauFrom`)로 넘긴다.
- **시간 고정 테스트**: `Clock`을 빈으로 두어 테스트에서 "오늘"을 고정한다.

## KPI 추가하기

`src/main/resources/kpi/`에 파일을 하나 만든다. 파일명 앞 숫자가 리포트 순서다.

```sql
-- code: refund_amount
-- name: 환불 금액
-- unit: 원
SELECT COALESCE(SUM(amount), 0)
FROM purchase
WHERE status = 'REFUNDED' AND paid_at >= :from AND paid_at < :to
```

과거 값이 필요하면 `POST /api/reports/backfill?from=...&to=...`로 다시 계산한다.

## 실행

```bash
docker compose up -d                                   # PostgreSQL 16 (포트 5433)
./gradlew bootRun --args='--spring.profiles.active=demo'  # 가상 데이터 생성 후 실행

curl -X POST "localhost:8080/api/reports/backfill?from=2026-07-01&to=2026-07-31"
curl localhost:8080/api/reports/2026-07-31
```

Slack으로도 받으려면 `kpi.slack.webhook-url`(환경변수 `SLACK_WEBHOOK_URL` 등)을 설정한다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/kpis` | KPI 목록 |
| GET | `/api/reports/{date}` | 저장된 리포트 (전일·전주 비교 포함) |
| POST | `/api/reports/{date}/run?send=false` | 수동 실행 |
| POST | `/api/reports/backfill?from=&to=` | 기간 재계산 (최대 93일) |
| GET | `/api/kpis/{code}/trend?from=&to=` | KPI 추이 |
| GET | `/api/runs` | 최근 실행 기록 |

## 테스트

```bash
./gradlew test
```

H2(PostgreSQL 모드)에 원천 데이터를 직접 넣고 값이 정확한지 확인한다. 중복 로그인, 환불 제외, MAU 30일 경계, 자정 경계, 재실행, 전송 실패, 백필을 다룬다.

## 한계와 다음 단계

- 서버를 여러 대 띄우면 스케줄러가 대수만큼 돈다 → ShedLock 같은 분산 락 필요
- 원천 데이터가 크면 운영 DB 대신 읽기 전용 복제본이나 집계 테이블을 봐야 한다
- 메일 전송, 이상치 알림(전주 대비 ±30% 등), 간단한 대시보드
