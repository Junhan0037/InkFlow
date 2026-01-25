import http from "k6/http";
import { check, sleep } from "k6";

// 부하 테스트 실행 환경 변수(기본값 포함)를 읽어온다.
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const CREATOR_ID = __ENV.CREATOR_ID || "creator-1";
const EPISODE_ID = parseInt(__ENV.EPISODE_ID || "1", 10);
const FILE_NAME = __ENV.FILE_NAME || "image.png";
const CONTENT_TYPE = __ENV.CONTENT_TYPE || "image/png";
const CHECKSUM = __ENV.CHECKSUM || "checksum-1";
const TOTAL_PARTS = parseInt(__ENV.TOTAL_PARTS || "1", 10);
const ETAG = __ENV.ETAG || "etag-1";
const SLEEP_SECONDS = parseFloat(__ENV.SLEEP_SECONDS || "1");
const SEARCH_ENABLED = (__ENV.SEARCH_ENABLED || "true") === "true";

export const options = buildOptions();

/**
 * 시나리오/임계값을 구성한다.
 */
function buildOptions() {
  const uploadVus = parseInt(__ENV.UPLOAD_VUS || "5", 10);
  const uploadDuration = __ENV.UPLOAD_DURATION || "1m";
  const searchVus = parseInt(__ENV.SEARCH_VUS || "5", 10);
  const searchDuration = __ENV.SEARCH_DURATION || "1m";

  const scenarios = {
    upload_flow: {
      executor: "constant-vus",
      vus: uploadVus,
      duration: uploadDuration,
      tags: { scenario: "upload_flow" },
    },
  };

  if (SEARCH_ENABLED) {
    scenarios.search_flow = {
      executor: "constant-vus",
      vus: searchVus,
      duration: searchDuration,
      tags: { scenario: "search_flow" },
      exec: "searchFlow",
    };
  }

  return {
    scenarios,
    thresholds: {
      // API 가용성과 응답 지연에 대한 SLO 기준을 반영한다.
      "http_req_failed{scenario:upload_flow}": ["rate<0.01"],
      "http_req_duration{name:upload_create}": ["p(95)<800"],
      "http_req_duration{name:upload_complete}": ["p(95)<1200"],
      "http_req_failed{scenario:search_flow}": ["rate<0.01"],
      "http_req_duration{name:search_assets}": ["p(95)<400"],
    },
  };
}

/**
 * 업로드 생성/완료 흐름을 반복한다.
 */
export default function uploadFlow() {
  const uploadId = createUploadSession();
  if (!uploadId) {
    return;
  }
  completeUploadSession(uploadId);
  sleep(SLEEP_SECONDS);
}

/**
 * 검색 호출을 반복한다.
 */
export function searchFlow() {
  const url =
    `${BASE_URL}/search/assets?episodeId=${EPISODE_ID}` +
    `&status=STORED&contentType=${encodeURIComponent(CONTENT_TYPE)}` +
    "&page=0&size=20";

  const response = http.get(url, {
    headers: buildHeaders(generateId("search")),
    tags: { name: "search_assets" },
  });

  check(response, {
    "검색 응답이 200이다": (res) => res.status === 200,
    "검색 응답에 data가 있다": (res) => !!safeJson(res)?.data,
  });

  sleep(SLEEP_SECONDS);
}

/**
 * 업로드 세션 생성 API를 호출한다.
 */
function createUploadSession() {
  const payload = JSON.stringify({
    episodeId: EPISODE_ID,
    filename: FILE_NAME,
    contentType: CONTENT_TYPE,
    size: 20,
    checksum: CHECKSUM,
    totalParts: TOTAL_PARTS,
  });

  const response = http.post(`${BASE_URL}/uploads`, payload, {
    headers: buildHeaders(generateId("upload-create")),
    tags: { name: "upload_create" },
  });

  const parsed = safeJson(response);
  const uploadId = parsed?.data?.uploadId;

  check(response, {
    "업로드 생성 응답이 200이다": (res) => res.status === 200,
    "업로드 생성 응답에 uploadId가 있다": () => !!uploadId,
  });

  return uploadId;
}

/**
 * 업로드 완료 API를 호출한다.
 */
function completeUploadSession(uploadId) {
  const payload = JSON.stringify({
    uploadedParts: [
      {
        partNumber: 1,
        etag: ETAG,
      },
    ],
    checksum: CHECKSUM,
  });

  const response = http.post(`${BASE_URL}/uploads/${uploadId}/complete`, payload, {
    headers: buildHeaders(generateId("upload-complete")),
    tags: { name: "upload_complete" },
  });

  const parsed = safeJson(response);
  const assetId = parsed?.data?.assetId;

  check(response, {
    "업로드 완료 응답이 200이다": (res) => res.status === 200,
    "업로드 완료 응답에 assetId가 있다": () => !!assetId,
  });
}

/**
 * 표준 헤더를 구성한다.
 */
function buildHeaders(idempotencyKey) {
  return {
    "Content-Type": "application/json",
    "X-User-Id": CREATOR_ID,
    "X-Request-Id": generateId("request"),
    "Idempotency-Key": idempotencyKey,
  };
}

/**
 * 응답 JSON 파싱을 안전하게 수행한다.
 */
function safeJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

/**
 * 테스트 실행 중 사용되는 유니크 식별자를 생성한다.
 */
function generateId(prefix) {
  const random = Math.floor(Math.random() * 1000000);
  return `${prefix}-${Date.now()}-${__VU}-${__ITER}-${random}`;
}
