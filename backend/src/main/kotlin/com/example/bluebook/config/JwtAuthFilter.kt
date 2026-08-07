package com.example.bluebook.config

import com.example.bluebook.common.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtUtil: JwtUtil,
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {
    companion object {
        val PUBLIC_PATHS = setOf(
            "/api/v2/auth/login", "/api/v2/auth/register", "/api/v2/auth/code",
            "/api/v2/auth/refresh", "/actuator/health", "/api/v2/feed",
            "/api/v2/videos/search", "/api/v1/comments"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val path = request.requestURI

        // GET requests and public paths: optional auth (for isLike/isFollow state)
        if (request.method == "GET" || PUBLIC_PATHS.any { path.startsWith(it) }) {
            val token = extractToken(request)
            if (token != null && jwtUtil.validateToken(token)) {
                val jti = jwtUtil.getJti(token)
                if (redisTemplate.opsForValue().get("token:blacklist:$jti") == null) {
                    setAuth(jwtUtil.getUserId(token))
                }
            }
            chain.doFilter(request, response)
            return
        }

        val token = extractToken(request)
        if (token == null) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = 401
            response.writer.write("""{"code":10005,"message":"请先登录","ttl":0,"data":null}""")
            return
        }
        if (!jwtUtil.validateToken(token)) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = 401
            response.writer.write("""{"code":10002,"message":"登录已过期，请重新登录","ttl":0,"data":null}""")
            return
        }
        val jti = jwtUtil.getJti(token)
        if (redisTemplate.opsForValue().get("token:blacklist:$jti") != null) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = 401
            response.writer.write("""{"code":10002,"message":"Token已失效","ttl":0,"data":null}""")
            return
        }
        setAuth(jwtUtil.getUserId(token))
        chain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            header.substring(7)
        else null
    }

    private fun setAuth(userId: Long) {
        val auth = UsernamePasswordAuthenticationToken(userId, null, listOf())
        SecurityContextHolder.getContext().authentication = auth
    }
}
