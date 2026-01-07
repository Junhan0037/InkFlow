package com.inkflow.common.error

/**
 * REST 응답 표준 envelope를 정의한다.
 */
data class ApiResponse<T>(
    val requestId: String,
    val code: String,
    val message: String,
    val data: T? = null
) {
    companion object {
        /**
         * 성공 응답을 생성한다.
         */
        fun <T> success(requestId: String, data: T, message: String = "success"): ApiResponse<T> {
            return ApiResponse(
                requestId = requestId,
                code = "OK",
                message = message,
                data = data
            )
        }
    }
}
