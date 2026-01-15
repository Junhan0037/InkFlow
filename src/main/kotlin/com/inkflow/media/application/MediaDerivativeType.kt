package com.inkflow.media.application

/**
 * Media 파생 리소스 타입을 정의한다.
 */
enum class MediaDerivativeType(val value: String) {
    /**
     * 썸네일 파생 타입.
     */
    THUMBNAIL("THUMBNAIL");

    companion object {
        /**
         * 문자열을 파생 타입으로 변환한다.
         */
        fun from(rawValue: String): MediaDerivativeType? {
            return entries.firstOrNull { it.value.equals(rawValue, ignoreCase = true) }
        }
    }
}
