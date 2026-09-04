package com.example.bluebook.common

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceFilter : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val traceId = UUID.randomUUID().toString().replace("-", "")
        MDC.put("traceId", traceId)
        if (response is HttpServletResponse) {
            response.setHeader("X-Trace-Id", traceId)
        }
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove("traceId")
        }
    }
}
