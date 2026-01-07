package com.inkflow.common.observability

/**
 * 스레드 로컬 기반의 Trace 컨텍스트 보관소다.
 */
object TraceContextHolder {
    private val holder = ThreadLocal<TraceContext?>()

    /**
     * 현재 Trace 컨텍스트를 조회한다.
     */
    fun current(): TraceContext? = holder.get()

    /**
     * Trace 컨텍스트를 설정하거나 제거한다.
     */
    fun set(context: TraceContext?) {
        if (context == null) {
            holder.remove()
        } else {
            holder.set(context)
        }
    }

    /**
     * Trace 컨텍스트를 지정한 범위에만 적용한다.
     */
    inline fun <T> withContext(context: TraceContext?, block: () -> T): T {
        val previous = current()
        set(context)
        return try {
            block()
        } finally {
            set(previous)
        }
    }
}
