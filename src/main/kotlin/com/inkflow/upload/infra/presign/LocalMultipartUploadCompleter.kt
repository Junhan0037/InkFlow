package com.inkflow.upload.infra.presign

import com.inkflow.upload.application.CompletedMultipartUpload
import com.inkflow.upload.application.CompletedPart
import com.inkflow.upload.application.MultipartUploadCompleter
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 로컬 환경에서 멀티파트 완료를 시뮬레이션하는 구현체.
 */
@Component
class LocalMultipartUploadCompleter : MultipartUploadCompleter {
    /**
     * 파트 목록을 기반으로 결정적인 ETag를 생성한다.
     */
    override fun completeMultipartUpload(
        bucket: String,
        key: String,
        uploadId: String,
        parts: List<CompletedPart>
    ): CompletedMultipartUpload {
        val etag = buildEtag(bucket, key, uploadId, parts)
        return CompletedMultipartUpload(etag = etag)
    }

    /**
     * 업로드 식별자와 파트 정보를 포함한 해시를 ETag로 만든다.
     */
    private fun buildEtag(
        bucket: String,
        key: String,
        uploadId: String,
        parts: List<CompletedPart>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val normalized = parts.sortedBy { it.partNumber }
            .joinToString("|") { "${it.partNumber}:${normalizeEtag(it.etag)}" }
        val payload = "$bucket|$key|$uploadId|$normalized"
        val hashed = digest.digest(payload.toByteArray(StandardCharsets.UTF_8)).toHex()
        return "\"$hashed\""
    }

    /**
     * ETag에서 따옴표와 공백을 제거해 비교가 가능하도록 정규화한다.
     */
    private fun normalizeEtag(etag: String): String {
        return etag.trim().trim('"')
    }

    /**
     * 바이트 배열을 16진 문자열로 변환한다.
     */
    private fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        for (value in this) {
            builder.append(String.format("%02x", value))
        }
        return builder.toString()
    }
}
