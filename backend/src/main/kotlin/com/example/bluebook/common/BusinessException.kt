package com.example.bluebook.common

open class BusinessException(
    val code: Int,
    override val message: String
) : RuntimeException(message)

// 认证相关 10001-10999
class InvalidCredentialsException : BusinessException(10001, "手机号或密码错误")
class TokenExpiredException : BusinessException(10002, "登录已过期，请重新登录")
class SmsRateLimitException : BusinessException(10003, "验证码已发送，请60秒后再试")
class AccountLockedException : BusinessException(10004, "账号已锁定，请15分钟后再试")
class UnauthorizedException : BusinessException(10005, "请先登录")

// 视频相关 11001-11999
class VideoNotFoundException : BusinessException(11001, "视频不存在或已被删除")
class TranscodeFailedException : BusinessException(11002, "视频转码失败，请重新上传")

// 评论相关 12001-12999
class CommentNotFoundException : BusinessException(12001, "评论不存在或已被删除")

// 文件相关 13001-13999
class FileTooLargeException : BusinessException(13001, "文件大小超过限制")
class ChunkMissingException : BusinessException(13002, "分片缺失，请重新上传缺失的分片")
class InvalidFileTypeException : BusinessException(13003, "不支持的文件格式")

// 通用 14001-14999
class ForbiddenException : BusinessException(14001, "无权执行此操作")
class ServerBusyException : BusinessException(14999, "服务器繁忙，请稍后再试")
