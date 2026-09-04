package com.example.bluebook.common

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${app.jwt.secret}") secret: String,
    @Value("\${app.jwt.access-token-ttl}") private val accessTtl: Long,
    @Value("\${app.jwt.refresh-token-ttl}") private val refreshTtl: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray().let { bytes ->
        if (bytes.size < 32) bytes + ByteArray(32 - bytes.size) { 0 } else bytes
    })

    fun generateAccessToken(userId: Long, phone: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("phone", phone)
            .issuedAt(now)
            .expiration(Date(now.time + accessTtl))
            .id(UUID.randomUUID().toString())
            .signWith(key)
            .compact()
    }

    fun generateRefreshToken(): String =
        "${UUID.randomUUID()}_${UUID.randomUUID()}_${System.currentTimeMillis()}"

    fun validateToken(token: String): Boolean = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
    }.isSuccess

    fun getUserId(token: String): Long = Jwts.parser()
        .verifyWith(key).build()
        .parseSignedClaims(token).payload.subject.toLong()

    fun getJti(token: String): String = Jwts.parser()
        .verifyWith(key).build()
        .parseSignedClaims(token).payload.id

    fun hashToken(token: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
