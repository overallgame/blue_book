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

/**
 * 分片参数不自洽：分片大小越界、分片数与文件大小对不上、分片序号越界、单片超限。
 *
 * message 由调用方给出**具体**原因（例如"分片序号越界：9999（应为 0…49）"）：
 * 这类错误只可能来自客户端 bug 或版本不一致，笼统的"请求参数有误"无法据此定位。
 * HTTP 状态为 400（落在 GlobalExceptionHandler 的 else 分支）。
 */
class InvalidUploadParamsException(message: String) : BusinessException(13005, message)

/**
 * 分片内容与客户端声称的指纹不符：**收到的字节不是它发出去的字节**。
 *
 * 由客户端在分片级别**原地重传**（它按 13006 这个码判断），所以 HTTP 状态是 400
 * 但语义上是"这次传输有问题、值得重试"，与 13005（参数错，重试无用）相反。
 * ★ 这个码是跨端契约：客户端 `ChunkedUploader` 里有一个同名常量按它分流。
 */
class PartChecksumMismatchException :
    BusinessException(13006, "分片校验失败，请重传该分片")

// 扫一扫相关 15001-15999
//
// 「这不是小蓝书的二维码」必须由**服务端**回答，而不是客户端先解析出 id 再传过来（设计方案 6.2）：
// 判据放在服务端，后端将来加新码格式时老版本 App 依然能扫出来（老版本 App 是改不动的）。
class NotBlueBookCodeException : BusinessException(15001, "这不是小蓝书的二维码")

/**
 * 码指向的内容存在、但当前不可见（例如审核中）。
 * 与 404 的区别是「有，你看不到」对「没有」——一律按 404 处理会把审核中的内容说成"已删除"。
 */
class ScanContentForbiddenException : BusinessException(15002, "该内容暂时无法查看")

/** 码本身合法，但指向的内容已经不在了。message 由调用方给出具体对象名。 */
class ScanTargetNotFoundException(message: String) : BusinessException(15003, message)

// 通用 14001-14999
class ForbiddenException : BusinessException(14001, "无权执行此操作")
class ServerBusyException : BusinessException(14999, "服务器繁忙，请稍后再试")
