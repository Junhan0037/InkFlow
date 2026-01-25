import grpc from "k6/net/grpc";
import { check, sleep } from "k6";

// gRPC 부하 테스트에 필요한 환경 설정을 로드한다.
const GRPC_TARGET = __ENV.GRPC_TARGET || "localhost:9091";
const PROTO_DIR = __ENV.PROTO_DIR || "../../docs/proto";
const EPISODE_ID = parseInt(__ENV.EPISODE_ID || "1", 10);
const REGION = __ENV.REGION || "KR";
const LANGUAGE = __ENV.LANGUAGE || "ko";
const SLEEP_SECONDS = parseFloat(__ENV.SLEEP_SECONDS || "1");

const client = new grpc.Client();
let connected = false;

client.load([PROTO_DIR], "inkflow/publish/v1/publish.proto");

export const options = {
  vus: parseInt(__ENV.VUS || "5", 10),
  duration: __ENV.DURATION || "1m",
  thresholds: {
    // gRPC 퍼블리시 생성 호출의 지연/오류 기준을 검증한다.
    "grpc_req_duration{name:create_snapshot}": ["p(95)<1000"],
    "grpc_req_failed": ["rate<0.01"],
  },
};

/**
 * 퍼블리시 스냅샷 생성 RPC를 반복 호출한다.
 */
export default function publishFlow() {
  ensureConnected();

  const requestId = generateId("publish");
  const payload = {
    episodeId: EPISODE_ID,
    region: REGION,
    language: LANGUAGE,
    requestId: requestId,
  };

  const response = client.invoke(
    "inkflow.publish.v1.PublishService/CreateSnapshot",
    payload,
    { tags: { name: "create_snapshot" } }
  );

  check(response, {
    "퍼블리시 응답 상태가 OK이다": (res) => res && res.status === grpc.StatusOK,
    "퍼블리시 응답에 snapshotId가 있다": (res) => !!res?.message?.snapshotId,
  });

  sleep(SLEEP_SECONDS);
}

/**
 * 테스트 종료 시 gRPC 연결을 정리한다.
 */
export function teardown() {
  if (connected) {
    client.close();
  }
}

/**
 * gRPC 클라이언트를 필요 시점에만 연결한다.
 */
function ensureConnected() {
  if (connected) {
    return;
  }
  client.connect(GRPC_TARGET, { plaintext: true });
  connected = true;
}

/**
 * 요청 식별자를 생성한다.
 */
function generateId(prefix) {
  const random = Math.floor(Math.random() * 1000000);
  return `${prefix}-${Date.now()}-${__VU}-${__ITER}-${random}`;
}
