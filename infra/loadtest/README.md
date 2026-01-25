# 부하 테스트 가이드

## 목적
- `k6`/`Gatling` 기반 부하 테스트 시나리오를 제공하고 SLO 검증의 기준선을 맞춘다.
- 업로드/검색/퍼블리시 핵심 경로의 응답 지연과 오류율을 정량적으로 관측한다.

## 사전 준비
1. 로컬 인프라 기동: `docker compose -f infra/docker-compose.yml up -d`
2. 테스트 데이터 시딩: `infra/seed/seed_local.sh` 실행
3. 애플리케이션 실행: `./gradlew bootRun`

> 업로드 테스트는 `episodeId`와 `creatorId`가 일치하는 데이터가 필요합니다.

## k6 실행
### 업로드/검색 부하 테스트
```bash
k6 run infra/loadtest/k6/upload_flow.js
```

### 퍼블리시 gRPC 부하 테스트
```bash
k6 run infra/loadtest/k6/publish_grpc.js
```

### 주요 환경 변수
- `BASE_URL`: REST API 기본 주소 (기본값 `http://localhost:8080`)
- `GRPC_TARGET`: gRPC 주소 (기본값 `localhost:9091`)
- `EPISODE_ID`: 대상 에피소드 ID (기본값 `1`)
- `CREATOR_ID`: 업로드 요청 사용자 ID (기본값 `creator-1`)
- `UPLOAD_VUS`, `UPLOAD_DURATION`: 업로드 부하 설정
- `SEARCH_VUS`, `SEARCH_DURATION`: 검색 부하 설정
- `VUS`, `DURATION`: gRPC 부하 설정

## Gatling 실행
Gatling 번들을 사용해 실행합니다.
```bash
./gatling.sh -s com.inkflow.loadtest.UploadFlowSimulation \
  -DBASE_URL=http://localhost:8080 \
  -DCREATOR_ID=creator-1 \
  -DEPISODE_ID=1 \
  -DUSERS_PER_SEC=5 \
  -DDURATION_SECONDS=60
```

## SLO 기준
`docs/slo.md`에 정의된 응답 지연/오류율 기준을 테스트 임계값으로 활용합니다.
