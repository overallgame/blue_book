package com.example.bluebook.common

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TraceFilter : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val traceId = UUID.randomUUID().toString().replace("-", "").take(16)
        MDC.put("traceId", traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
