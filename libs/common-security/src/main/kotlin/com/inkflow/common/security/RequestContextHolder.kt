package com.inkflow.common.security

/**
 * 스레드 로컬 기반 RequestContext 보관소.
 */
object RequestContextHolder {
    private val holder = ThreadLocal<RequestContext?>()

    /**
     * 현재 RequestContext를 조회한다.
     */
    fun current(): RequestContext? = holder.get()

    /**
     * RequestContext를 설정하거나 제거한다.
     */
    fun set(context: RequestContext?) {
        if (context == null) {
            holder.remove()
        } else {
            holder.set(context)
        }
    }

    /**
     * 지정한 범위에서만 RequestContext를 적용한다.
     */
    inline fun <T> withContext(context: RequestContext?, block: () -> T): T {
        val previous = current()
        set(context)
        return try {
            block()
        } finally {
            set(previous)
        }
    }
}
