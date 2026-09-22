package com.example.bluebook.scan

/**
 * 一段合法的站内码指向哪里。只表达「指向谁」，不含展示信息——
 * 展示信息是 [ScanCodeFormat] 之后由 `ScanService` 查库补的。
 */
sealed interface ScanCode {

	/** `https://<码域名>/v/{aid}` */
	data class Video(val aid: Long) : ScanCode

	/** `https://<码域名>/u/{id}` */
	data class User(val id: Long) : ScanCode
}
