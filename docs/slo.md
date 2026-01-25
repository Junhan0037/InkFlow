# 서비스 SLO 기준

## 목적
- 핵심 API/gRPC 경로의 품질 기준을 정량화한다.
- 부하 테스트/운영 모니터링에서 동일한 기준을 사용한다.

## 공통 SLI 정의
- **성공률**: `2xx`(REST) 또는 `OK`(gRPC) 응답 비율
- **지연 시간**: 서버 측 응답 시간의 `p95`를 기준으로 판단
- **관측 기간**: 1분 롤링 윈도우 + 월간 집계

## SLO 기준
### Upload API
- `POST /uploads` p95 ≤ 800ms, 오류율 ≤ 1%
- `POST /uploads/{id}/complete` p95 ≤ 1200ms, 오류율 ≤ 1%

### Search API
- `GET /search/works` p95 ≤ 400ms, 오류율 ≤ 1%
- `GET /search/episodes` p95 ≤ 400ms, 오류율 ≤ 1%
- `GET /search/assets` p95 ≤ 400ms, 오류율 ≤ 1%

### Publish gRPC
- `PublishService.CreateSnapshot` p95 ≤ 1000ms, 오류율 ≤ 1%
- `PublishService.Rollback` p95 ≤ 1000ms, 오류율 ≤ 1%

## 비고
- 대용량 업로드는 별도 SLO로 분리해 측정한다.
- 배치/백필 작업은 처리량 기반 SLO를 사용한다.
