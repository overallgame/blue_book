package com.example.bluebook.auth.repository

import com.example.bluebook.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userId = :userId")
    fun deleteAllByUserId(userId: Long)

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    fun deleteAllExpired(now: Instant)
}
