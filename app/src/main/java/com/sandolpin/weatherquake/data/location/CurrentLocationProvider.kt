package com.sandolpin.weatherquake.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 検索シートの「現在地から取得」機能で使う、端末の現在地取得・逆ジオコーディングをまとめたヘルパー。
 *
 * Open-Meteoのジオコーディング(WeatherRepository)は地名→緯度経度の変換(順ジオコーディング)しか
 * 提供していないため、緯度経度→地名(逆ジオコーディング)にはAndroid標準のGeocoderクラスを使う。
 */
object CurrentLocationProvider {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 端末の現在地(GPSが有効ならGPS、無ければネットワーク測位)を1回だけ取得する。
     * 位置情報の権限が無い/プロバイダが無効/取得失敗した場合はnullを返す。
     */
    suspend fun getCurrentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission(context)) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider = when {
            locationManager == null -> null
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (locationManager == null || provider == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        try {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                context.mainExecutor
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (e: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    /** 緯度経度から地名(市区町村名)を逆ジオコーディングする。取得できない場合は「現在地」を返す。 */
    fun reverseGeocodeName(context: Context, latitude: Double, longitude: Double): String {
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.JAPAN)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull()
            address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "現在地"
        } catch (e: Exception) {
            "現在地"
        }
    }
}