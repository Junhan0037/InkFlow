package com.inkflow.media.application

/**
 * Media 파생 리소스가 저장되는 오브젝트 스토리지 위치를 표현한다.
 */
data class MediaStorageLocation(
    val bucket: String,
    val key: String
) {
    init {
        require(bucket.isNotBlank()) { "bucket은 비어 있을 수 없습니다." }
        require(key.isNotBlank()) { "key는 비어 있을 수 없습니다." }
    }
}

/**
 * Media 스토리지에서 읽은 객체 정보를 전달한다.
 */
data class MediaStorageObject(
    val contentType: String?,
    val bytes: ByteArray
) {
    init {
        require(bytes.isNotEmpty()) { "bytes는 비어 있을 수 없습니다." }
    }
}

/**
 * Media 워커가 사용할 스토리지 접근 계약을 정의한다.
 */
interface MediaStorageClient {
    /**
     * 스토리지에서 객체를 다운로드한다.
     */
    fun download(location: MediaStorageLocation): MediaStorageObject

    /**
     * 스토리지에 객체를 업로드한다.
     */
    fun upload(location: MediaStorageLocation, contentType: String, bytes: ByteArray)
}
