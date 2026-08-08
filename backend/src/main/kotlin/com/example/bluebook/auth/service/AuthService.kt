package com.example.bluebook.auth.service

import com.example.bluebook.auth.dto.*
import com.example.bluebook.auth.entity.RefreshToken
import com.example.bluebook.auth.entity.User
import com.example.bluebook.auth.repository.RefreshTokenRepository
import com.example.bluebook.auth.repository.UserRepository
import com.example.bluebook.common.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val redisTemplate: StringRedisTemplate
) {
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val failKey = "login:fail:${request.phone}"
        val failCount = redisTemplate.opsForValue().get(failKey)?.toIntOrNull() ?: 0
        if (failCount >= 5) throw AccountLockedException()

        val user = userRepository.findByPhone(request.phone)
            .orElseThrow { InvalidCredentialsException() }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            val ops = redisTemplate.opsForValue()
            ops.increment(failKey)
            redisTemplate.expire(failKey, Duration.ofMinutes(15))
            throw InvalidCredentialsException()
        }
        redisTemplate.delete(failKey)
        return buildLoginResponse(user)
    }

    @Transactional
    fun register(request: RegisterRequest): LoginResponse {
        val codeKey = "sms:${request.phone}"
        val storedCode = redisTemplate.opsForValue().get(codeKey)
        if (storedCode == null || storedCode != request.code)
            throw BusinessException(10006, "验证码错误或已过期")
        redisTemplate.delete(codeKey)

        if (userRepository.existsByPhone(request.phone))
            throw BusinessException(10007, "该手机号已注册")

        val user = User(
            phone = request.phone,
            nickname = request.nickname,
            passwordHash = passwordEncoder.encode(request.password)
        )
        userRepository.save(user)
        return buildLoginResponse(user)
    }

    fun sendCode(request: SendCodeRequest): String {
        val phoneKey = "sms:phone:${request.phone}"
        if (redisTemplate.opsForValue().get(phoneKey) != null)
            throw SmsRateLimitException()

        val code = "%06d".format((Math.random() * 1000000).toInt())
        redisTemplate.opsForValue().set("sms:${request.phone}", code, Duration.ofMinutes(5))
        redisTemplate.opsForValue().set(phoneKey, "1", Duration.ofSeconds(60))
        return code // Return code in dev mode for testing convenience
    }

    @Transactional
    fun refresh(refreshTokenStr: String): LoginResponse {
        val hash = jwtUtil.hashToken(refreshTokenStr)
        val rt = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow { TokenExpiredException() }
        if (rt.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt)
            throw TokenExpiredException()
        }
        refreshTokenRepository.delete(rt)
        val user = userRepository.findById(rt.userId).orElseThrow { TokenExpiredException() }
        return buildLoginResponse(user)
    }

    @Transactional
    fun refreshTokens(refreshTokenStr: String): TokenResponse {
        val hash = jwtUtil.hashToken(refreshTokenStr)
        val rt = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow { TokenExpiredException() }
        if (rt.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt)
            throw TokenExpiredException()
        }
        refreshTokenRepository.delete(rt)
        val user = userRepository.findById(rt.userId).orElseThrow { TokenExpiredException() }
        val accessToken = jwtUtil.generateAccessToken(user.id, user.phone)
        val newRefreshToken = jwtUtil.generateRefreshToken()
        refreshTokenRepository.save(RefreshToken(
            userId = user.id,
            tokenHash = jwtUtil.hashToken(newRefreshToken),
            expiresAt = Instant.now().plusMillis(604800000)
        ))
        return TokenResponse(token = accessToken, refreshToken = newRefreshToken)
    }

    fun logout(userId: Long) {
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    fun addToBlacklist(jti: String) {
        redisTemplate.opsForValue().set("token:blacklist:$jti", "1", Duration.ofDays(1))
    }

    private fun buildLoginResponse(user: User): LoginResponse {
        val accessToken = jwtUtil.generateAccessToken(user.id, user.phone)
        val refreshTokenStr = jwtUtil.generateRefreshToken()
        val hash = jwtUtil.hashToken(refreshTokenStr)
        refreshTokenRepository.save(RefreshToken(
            userId = user.id,
            tokenHash = hash,
            expiresAt = Instant.now().plusMillis(604800000) // 7d
        ))
        val maskedPhone = user.phone.replaceRange(3, 7, "****")
        return LoginResponse(
            token = accessToken,
            refreshToken = refreshTokenStr,
            profile = UserProfile(
                id = user.id, phone = maskedPhone, nickname = user.nickname,
                avatar = user.avatarUrl, bio = user.bio, gender = user.gender,
                birthday = user.birthday?.toString(), occupation = user.occupation,
                region = user.region, school = user.school,
                background = user.backgroundUrl,
                followerCount = user.followerCount, followingCount = user.followingCount
            )
        )
    }
}
