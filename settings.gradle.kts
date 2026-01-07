rootProject.name = "InkFlow"

// 공통 이벤트 라이브러리를 멀티 모듈로 관리한다.
include("libs:common-events")

// 공통 관측성 라이브러리를 멀티 모듈로 관리한다.
include("libs:common-observability")

// 공통 보안 라이브러리를 멀티 모듈로 관리한다.
include("libs:common-security")

// 공통 gRPC 라이브러리를 멀티 모듈로 관리한다.
include("libs:common-grpc")
