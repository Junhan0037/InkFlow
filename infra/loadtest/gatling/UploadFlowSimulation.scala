package com.inkflow.loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import java.util.UUID

/**
 * 업로드 세션 생성/완료 흐름을 부하로 검증하는 Gatling 시뮬레이션.
 */
class UploadFlowSimulation extends Simulation {
  // 시스템 프로퍼티로 런타임 파라미터를 주입받는다.
  private val baseUrl = System.getProperty("BASE_URL", "http://localhost:8080")
  private val creatorId = System.getProperty("CREATOR_ID", "creator-1")
  private val episodeId = System.getProperty("EPISODE_ID", "1").toLong
  private val filename = System.getProperty("FILE_NAME", "image.png")
  private val contentType = System.getProperty("CONTENT_TYPE", "image/png")
  private val checksum = System.getProperty("CHECKSUM", "checksum-1")
  private val usersPerSec = System.getProperty("USERS_PER_SEC", "5").toDouble
  private val durationSeconds = System.getProperty("DURATION_SECONDS", "60").toInt

  // 공통 HTTP 설정과 인증 헤더를 구성한다.
  private val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .header("X-User-Id", creatorId)

  // 요청 추적/멱등성 키를 반복적으로 생성하는 피더를 사용한다.
  private val idempotencyFeeder = Iterator.continually(
    Map(
      "idempotencyKey" -> s"idemp-${UUID.randomUUID()}",
      "requestId" -> s"req-${UUID.randomUUID()}"
    )
  )

  // 업로드 세션 생성 요청을 정의한다.
  private val createSession = exec(
    http("upload_create")
      .post("/uploads")
      .header("Idempotency-Key", "${idempotencyKey}")
      .header("X-Request-Id", "${requestId}")
      .body(
        StringBody(
          s"""
             |{
             |  "episodeId": $episodeId,
             |  "filename": "$filename",
             |  "contentType": "$contentType",
             |  "size": 20,
             |  "checksum": "$checksum",
             |  "totalParts": 1
             |}
             |""".stripMargin
        )
      ).asJson
      .check(status.is(200))
      .check(jsonPath("$.data.uploadId").saveAs("uploadId"))
  )

  // 업로드 완료 요청을 정의한다.
  private val completeSession = exec(
    http("upload_complete")
      .post("/uploads/${uploadId}/complete")
      .header("Idempotency-Key", "${idempotencyKey}")
      .header("X-Request-Id", "${requestId}")
      .body(
        StringBody(
          s"""
             |{
             |  "uploadedParts": [
             |    { "partNumber": 1, "etag": "etag-1" }
             |  ],
             |  "checksum": "$checksum"
             |}
             |""".stripMargin
        )
      ).asJson
      .check(status.is(200))
  )

  // 업로드 생성→완료 흐름을 시나리오로 구성한다.
  private val scenarioFlow = scenario("UploadFlow")
    .feed(idempotencyFeeder)
    .exec(createSession)
    .exec(completeSession)
    .pause(1)

  // 트래픽 주입과 SLO 기반 검증을 설정한다.
  setUp(
    scenarioFlow.inject(
      constantUsersPerSec(usersPerSec).during(durationSeconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.failedRequests.percent.lte(1),
      global.responseTime.percentile3.lte(1200)
    )
}
