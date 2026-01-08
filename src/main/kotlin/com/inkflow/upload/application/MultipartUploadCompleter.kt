package com.inkflow.upload.application

/**
 * 멀티파트 업로드 완료 처리를 위한 계약.
 */
interface MultipartUploadCompleter {
    /**
     * 멀티파트 업로드를 완료하고 최종 ETag를 반환한다.
     */
    fun completeMultipartUpload(
        bucket: String,
        key: String,
        uploadId: String,
        parts: List<CompletedPart>
    ): CompletedMultipartUpload
}

/**
 * 멀티파트 업로드 완료 결과를 표현한다.
 */
data class CompletedMultipartUpload(
    val etag: String
)
