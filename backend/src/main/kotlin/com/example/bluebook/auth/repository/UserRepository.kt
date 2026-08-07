package com.example.bluebook.auth.repository

import com.example.bluebook.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByPhone(phone: String): Optional<User>
    fun existsByPhone(phone: String): Boolean
}
