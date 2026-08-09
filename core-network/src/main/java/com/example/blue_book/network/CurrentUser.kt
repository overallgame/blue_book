package com.example.blue_book.network

import com.example.blue_book.data.UserAccount
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前登录用户的内存单例。
 * 登录/注册后写入，退出登录时清空，冷启动从 Room 恢复。
 * 所有模块可通过 [@Inject] 直接读取。
 */
@Singleton
class CurrentUser @Inject constructor() {

    @Volatile
    var account: UserAccount? = null
        private set

    val isLoggedIn: Boolean get() = account != null

    val phone: String? get() = account?.phone
    val nickname: String? get() = account?.nickname
    val avatar: String? get() = account?.avatar
    val userId: Long? get() = account?.id

    fun restore(account: UserAccount) {
        this.account = account
    }

    fun updateField(block: (UserAccount) -> UserAccount) {
        account = account?.let(block)
    }

    fun clear() {
        account = null
    }
}
