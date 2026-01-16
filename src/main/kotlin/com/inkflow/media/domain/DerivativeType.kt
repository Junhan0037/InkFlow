package com.inkflow.media.domain

/**
 * 파생 리소스 유형을 정의.
 */
enum class DerivativeType(val value: String) {
    THUMBNAIL("THUMBNAIL"),
    RESIZED("RESIZED"),
    TRANSCODED("TRANSCODED");

    companion object {
        /**
         * 문자열 값을 파생 리소스 유형으로 변환한다.
         */
        fun from(rawValue: String): DerivativeType? {
            return entries.firstOrNull { it.value.equals(rawValue, ignoreCase = true) }
        }
    }
}
