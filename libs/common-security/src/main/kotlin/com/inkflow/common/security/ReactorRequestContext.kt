package com.inkflow.common.security

import reactor.util.context.Context
import reactor.util.context.ContextView

/**
 * 리액터 Context에 RequestContext를 저장/조회하기 위한 헬퍼.
 */
object ReactorRequestContext {
    private val CONTEXT_KEY: Any = RequestContext::class.java

    /**
     * 리액터 Context에 RequestContext를 저장한다.
     */
    fun put(context: Context, requestContext: RequestContext): Context {
        return context.put(CONTEXT_KEY, requestContext)
    }

    /**
     * 리액터 Context에서 RequestContext를 조회한다.
     */
    fun get(contextView: ContextView): RequestContext? {
        return if (contextView.hasKey(CONTEXT_KEY)) {
            contextView.get<RequestContext>(CONTEXT_KEY)
        } else {
            null
        }
    }
}
