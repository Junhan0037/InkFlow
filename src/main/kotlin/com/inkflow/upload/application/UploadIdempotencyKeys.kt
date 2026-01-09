package com.inkflow.upload.application

/**
 * 업로드 API에서 사용하는 Idempotency 키 생성 규칙을 정의한다.
 */
object UploadIdempotencyKeys {
    /**
     * 업로드 세션 생성 요청의 Idempotency 키를 생성한다.
     */
    fun forCreateSession(creatorId: String, idempotencyKey: String): String {
        return "upload:create:$creatorId:$idempotencyKey"
    }

    /**
     * 업로드 완료 요청의 Idempotency 키를 생성한다.
     */
    fun forCompleteSession(creatorId: String, uploadId: String, idempotencyKey: String): String {
        return "upload:complete:$creatorId:$uploadId:$idempotencyKey"
    }
}
