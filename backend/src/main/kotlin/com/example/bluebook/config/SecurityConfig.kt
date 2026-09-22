package com.example.bluebook.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/api/v2/auth/**", "/actuator/health", "/actuator/info",
                    "/api/v2/feed", "/api/v2/videos/search", "/api/v2/videos/*/dto",
                    "/api/v2/videos/*/playUrl",
                    "/api/v2/users/*", "/api/v2/users/*/followers",
                    "/api/v2/users/*/following", "/api/v2/users/*/videos",
                    // 与 /search/hot 同类的只读接口，游客的「猜你想搜」此前始终回退到静态兜底
                    "/api/v2/search/hot", "/api/v2/search/suggest",
                    // 扫一扫校验：游客也能扫。它只返回"这个码指向什么内容"，
                    // 与 /videos/*/dto 同级；内容本身的可见性由 service 判（403/404）
                    "/api/v2/scan/resolve"
                ).permitAll()
                // 评论：仅放开读接口（游客可看评论与展开回复）。
                // 写接口（POST/DELETE）必须登录，否则会以 userId = 0 落库成"无作者评论"。
                // 注意 /replies 此前不在任何白名单里，游客展开回复拿到的是空 body 的裸 403。
                auth.requestMatchers(
                    HttpMethod.GET, "/api/v1/comments", "/api/v1/comments/**"
                ).permitAll()
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
