package com.example.blue_book.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 简易定位（无第三方依赖，供各 feature 共用）：
 * 读取最近一次已知位置并逆地理编码为城市名。
 * 不做连续定位；调用方负责权限检查；失败返回 null 由上层降级（回退全量流）。
 */
object LocationHelper {

	/** 当前城市名（如"杭州市"）；无权限/无缓存位置/解析失败均返回 null */
	suspend fun currentCity(context: Context): String? = withContext(Dispatchers.IO) {
		try {
			val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
				?: return@withContext null
			val location = lastKnownLocation(manager) ?: return@withContext null
			reverseGeocode(context, location)
		} catch (_: Throwable) {
			null
		}
	}

	@SuppressLint("MissingPermission")
	private fun lastKnownLocation(manager: LocationManager): Location? {
		val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
		return providers
			.mapNotNull { provider ->
				runCatching {
					if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
				}.getOrNull()
			}
			.maxByOrNull { it.time }
	}

	private fun reverseGeocode(context: Context, location: Location): String? {
		val geocoder = Geocoder(context, Locale.CHINA)
		@Suppress("DEPRECATION")
		val address = runCatching {
			geocoder.getFromLocation(location.latitude, location.longitude, 1)
		}.getOrNull()?.firstOrNull() ?: return null
		return address.locality ?: address.subAdminArea ?: address.adminArea
	}
}
