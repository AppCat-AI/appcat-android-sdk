/**
 * AppCat Android SDK — deferred deep link resolution and attribution.
 *
 * Open-source thin wrapper around the closed-source com.appcat:core AAR.
 *
 * Usage:
 *   val response = AppCat.configureAsync(context, "apiKey", "appId")
 *   response.deepLinkParams?.let { /* handle deep link */ }
 *   val identity = AppCat.identifyAsync(mapOf("userId" to "123", "email" to "user@example.com"))
 *   AppCat.sendEvent("Purchase", mapOf("value" to 9.99, "currency" to "USD", "eventId" to "purchase_$orderId"))
 *   val attribution = AppCat.getAttribution()
 */

package com.appcat.sdk

import android.content.Context
import android.util.Log
import com.appcat.core.AppCatCore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// MARK: - Public types

/** Geo data resolved from the device's IP during attribution. */
data class AppCatGeoResponse(
  /** City name, or null if unavailable. */
  val city: String? = null,
  /** ISO country code, or null if unavailable. */
  val country: String? = null,
  /** State/region/province, or null if unavailable. */
  val state: String? = null,
)

/** Structured response from configure(). */
data class AppCatInitResponse(
  /** Deep link query params from the matched ad click URL, or null if no match. */
  val deepLinkParams: Map<String, String>? = null,
  /** Geo data resolved from the device's IP, or null if unavailable. */
  val geo: AppCatGeoResponse? = null,
)

/** Structured response from identify(). */
data class AppCatIdentifyResponse(
  /** Geo data from the attribution profile, or null if unavailable. */
  val geo: AppCatGeoResponse? = null,
  /** Deep link params from the attribution profile, or null if none. */
  val deepLinkParams: Map<String, String>? = null,
)

class AppCatSDKException(message: String) : Exception(message)

/** Log verbosity level. */
enum class AppCatLogLevel(val value: Int) {
  DEBUG(0),
  INFO(1),
  WARN(2),
  ERROR(3);
}

// MARK: - Public API

object AppCat {

  private const val TAG = "AppCat"

  private var isConfiguredFlag = false
  private var cachedDeepLinkParams: Map<String, String>? = null

  // --- Internal safety helpers ---

  /**
   * Invoke a host-supplied callback while swallowing any [Throwable] it raises.
   * Host code must never be able to crash the SDK thread or propagate back into core.
   */
  private fun <T> safeInvoke(cb: ((Result<T>) -> Unit)?, result: Result<T>) {
    if (cb == null) return
    try {
      cb.invoke(result)
    } catch (t: Throwable) {
      Log.e(TAG, "Host callback threw", t)
    }
  }

  // --- Callback-based API ---

  /**
   * Initialize the SDK and create the attribution profile.
   *
   * Configures credentials, resolves attribution, and returns an
   * [AppCatInitResponse] with `deepLinkParams` (or null if no match).
   */
  fun configure(
    context: Context,
    apiKey: String,
    appId: String = "",
    options: Map<String, Any?> = emptyMap(),
    callback: ((Result<AppCatInitResponse>) -> Unit)? = null
  ) {
    try {
      AppCatCore.instance.configure(
        context = context,
        appId = appId,
        apiKey = apiKey,
        options = options,
        callback = object : AppCatCore.ResultCallback<Boolean> {
          override fun onSuccess(result: Boolean) {
            isConfiguredFlag = true
            try {
              AppCatCore.instance.resolve(
                ddlToken = null,
                callback = object : AppCatCore.ResultCallback<Map<String, Any?>> {
                  override fun onSuccess(result: Map<String, Any?>) {
                    val response = try {
                      @Suppress("UNCHECKED_CAST")
                      val params = result["deepLinkParams"] as? Map<String, String>
                      val deepLinks = if (params != null && params.isNotEmpty()) params else null
                      cachedDeepLinkParams = deepLinks
                      val rawGeo = result["geo"] as? Map<*, *>
                      val geo = if (rawGeo != null) AppCatGeoResponse(
                        city = rawGeo["city"] as? String,
                        country = rawGeo["country"] as? String,
                        state = rawGeo["state"] as? String,
                      ) else null
                      AppCatInitResponse(deepLinkParams = deepLinks, geo = geo)
                    } catch (t: Throwable) {
                      Log.e(TAG, "configure: failed to parse resolve result", t)
                      AppCatInitResponse(deepLinkParams = null, geo = null)
                    }
                    safeInvoke(callback, Result.success(response))
                  }
                  override fun onError(code: String, message: String) {
                    // Resolve failure is non-fatal — SDK is still configured
                    safeInvoke(callback, Result.success(AppCatInitResponse(deepLinkParams = null, geo = null)))
                  }
                }
              )
            } catch (t: Throwable) {
              Log.e(TAG, "configure: resolve invocation failed", t)
              safeInvoke(callback, Result.success(AppCatInitResponse(deepLinkParams = null, geo = null)))
            }
          }
          override fun onError(code: String, message: String) {
            safeInvoke(callback, Result.failure(AppCatSDKException("[$code] $message")))
          }
        }
      )
    } catch (t: Throwable) {
      Log.e(TAG, "configure failed", t)
      safeInvoke(callback, Result.failure(AppCatSDKException(t.message ?: "configure failed")))
    }
  }

  /**
   * Enrich the user profile with additional data.
   *
   * Recognized keys: `userId`, `email`, `phone`, `name`, `revenueCatIds` (list of strings), `geo`, `customAttributes`.
   *
   * Returns geo and deepLinkParams from the attribution profile.
   */
  fun identify(
    data: Map<String, Any?>,
    callback: ((Result<AppCatIdentifyResponse>) -> Unit)? = null
  ) {
    if (!isConfiguredFlag) {
      safeInvoke(callback, Result.failure(AppCatSDKException("Call configure() first")))
      return
    }

    try {
      AppCatCore.instance.identify(
        data = data,
        callback = object : AppCatCore.ResultCallback<Map<String, Any?>?> {
          override fun onSuccess(result: Map<String, Any?>?) {
            val response = try {
              @Suppress("UNCHECKED_CAST")
              val serverData = result?.get("data") as? Map<String, Any?>
              val rawGeo = serverData?.get("geo") as? Map<*, *>
              val geo = if (rawGeo != null) AppCatGeoResponse(
                city = rawGeo["city"] as? String,
                country = rawGeo["country"] as? String,
                state = rawGeo["state"] as? String,
              ) else null
              @Suppress("UNCHECKED_CAST")
              val dlp = serverData?.get("deepLinkParams") as? Map<String, String>
              AppCatIdentifyResponse(geo = geo, deepLinkParams = dlp)
            } catch (t: Throwable) {
              Log.e(TAG, "identify: failed to parse result", t)
              AppCatIdentifyResponse(geo = null, deepLinkParams = null)
            }
            safeInvoke(callback, Result.success(response))
          }
          override fun onError(code: String, message: String) {
            safeInvoke(callback, Result.failure(AppCatSDKException("[$code] $message")))
          }
        }
      )
    } catch (t: Throwable) {
      Log.e(TAG, "identify failed", t)
      safeInvoke(callback, Result.failure(AppCatSDKException(t.message ?: "identify failed")))
    }
  }

  /**
   * Update the user's tracking-consent choice.
   *
   * Call when the user makes a consent decision in your app (e.g. after an
   * ATT prompt, GDPR banner, or settings toggle). When consent is denied,
   * AppCat avoids forwarding certain PII fields (such as email and phone)
   * to ad networks on your behalf. Default behavior (when never called) is
   * unchanged.
   *
   * Fire-and-forget: the optional [callback] is invoked with
   * `Result.success(Unit)` on HTTP 2xx, or `Result.failure(...)` otherwise.
   */
  fun setTrackingConsent(
    granted: Boolean,
    callback: ((Result<Unit>) -> Unit)? = null
  ) {
    if (!isConfiguredFlag) {
      safeInvoke(callback, Result.failure(AppCatSDKException("Call configure() first")))
      return
    }
    try {
      // Wrap host callback so a throwing consumer can't crash core's dispatch thread.
      val safeCallback: ((Result<Unit>) -> Unit)? = if (callback == null) null else { result ->
        safeInvoke(callback, result)
      }
      AppCatCore.instance.setTrackingConsent(granted, safeCallback)
    } catch (t: Throwable) {
      Log.e(TAG, "setTrackingConsent failed", t)
      safeInvoke(callback, Result.failure(AppCatSDKException(t.message ?: "setTrackingConsent failed")))
    }
  }

  /**
   * Track a conversion event. Fire-and-forget.
   *
   * Pass all event data in a single flat map. Reserved keys
   * (`eventId`, `value`, `currency`, `testEventCode`) are forwarded as
   * options; all other keys become `custom_data` on the event.
   *
   * ```kotlin
   * AppCat.sendEvent("ViewContent", mapOf("eventId" to "vc_home_$sessionId"))
   * AppCat.sendEvent("Purchase", mapOf("orderId" to orderId, "value" to 9.99, "currency" to "USD", "eventId" to "purchase_$orderId"))
   * ```
   */
  fun sendEvent(
    eventName: String,
    params: Map<String, Any?>? = null
  ) {
    try {
      if (BuildConfig.DEBUG) {
        val revenueEvents = setOf("Purchase", "InitiateCheckout")
        if (eventName in revenueEvents) {
          val hasValue = params?.containsKey("value") == true
          val hasCurrency = params?.containsKey("currency") == true
          if (!hasValue || !hasCurrency) {
            Log.w(TAG, "'$eventName' is missing value or currency. Meta and TikTok will silently drop this event without both fields.")
          }
        }
      }
      val reserved = setOf("eventId", "value", "currency", "testEventCode")
      val customData = mutableMapOf<String, Any?>()
      val options = mutableMapOf<String, Any?>()
      params?.forEach { (key, value) ->
        if (key in reserved) options[key] = value else customData[key] = value
      }
      AppCatCore.instance.sendEvent(
        eventName = eventName,
        params = customData.ifEmpty { null },
        options = options.ifEmpty { null },
      )
    } catch (t: Throwable) {
      Log.e(TAG, "sendEvent failed", t)
    }
  }

  /** Get cached attribution data. Sync — no API call. */
  fun getAttribution(): Map<String, Any?>? {
    return try {
      AppCatCore.instance.getAttribution()
    } catch (t: Throwable) {
      Log.e(TAG, "getAttribution failed", t)
      null
    }
  }

  /** Get cached device context. */
  fun getDeviceContext(): Map<String, Any?>? {
    return try {
      AppCatCore.instance.getDeviceContext()
    } catch (t: Throwable) {
      Log.e(TAG, "getDeviceContext failed", t)
      null
    }
  }

  /** Whether the SDK has been configured. */
  fun isInitialized(): Boolean = isConfiguredFlag

  /** Get the stable AppCat device identifier. Returns Android ID. */
  fun getAppCatId(): String? = try {
    AppCatCore.instance.getAppCatId().ifEmpty { null }
  } catch (t: Throwable) {
    Log.e(TAG, "getAppCatId failed", t)
    null
  }

  /** Check if the SDK has been remotely disabled. */
  fun isDisabled(): Boolean = try {
    AppCatCore.instance.isDisabled()
  } catch (t: Throwable) {
    Log.e(TAG, "isDisabled failed", t)
    false
  }

  /** Set log verbosity level. */
  fun setLogLevel(level: AppCatLogLevel) {
    try {
      AppCatCore.instance.setLogLevel(level.value)
    } catch (t: Throwable) {
      Log.e(TAG, "setLogLevel failed", t)
    }
  }

  // --- Coroutine-based API ---

  /**
   * Suspend version of configure. Returns [AppCatInitResponse].
   */
  suspend fun configureAsync(
    context: Context,
    apiKey: String,
    appId: String = "",
    options: Map<String, Any?> = emptyMap()
  ): AppCatInitResponse = suspendCancellableCoroutine { cont ->
    try {
      configure(context, apiKey, appId, options) { result ->
        result.onSuccess { if (cont.isActive) cont.resume(it) }
        result.onFailure { if (cont.isActive) cont.resumeWithException(it) }
      }
    } catch (t: Throwable) {
      Log.e(TAG, "configureAsync dispatch failed", t)
      if (cont.isActive) cont.resumeWithException(AppCatSDKException(t.message ?: "configureAsync failed"))
    }
  }

  /** Suspend version of identify. Returns [AppCatIdentifyResponse]. */
  suspend fun identifyAsync(data: Map<String, Any?>): AppCatIdentifyResponse =
    suspendCancellableCoroutine { cont ->
      try {
        identify(data) { result ->
          result.onSuccess { if (cont.isActive) cont.resume(it) }
          result.onFailure { if (cont.isActive) cont.resumeWithException(it) }
        }
      } catch (t: Throwable) {
        Log.e(TAG, "identifyAsync dispatch failed", t)
        if (cont.isActive) cont.resumeWithException(AppCatSDKException(t.message ?: "identifyAsync failed"))
      }
    }

  /** Suspend version of setTrackingConsent. Completes on HTTP 2xx, throws on failure. */
  suspend fun setTrackingConsentAsync(granted: Boolean): Unit =
    suspendCancellableCoroutine { cont ->
      try {
        setTrackingConsent(granted) { result ->
          result.onSuccess { if (cont.isActive) cont.resume(Unit) }
          result.onFailure { if (cont.isActive) cont.resumeWithException(it) }
        }
      } catch (t: Throwable) {
        Log.e(TAG, "setTrackingConsentAsync dispatch failed", t)
        if (cont.isActive) cont.resumeWithException(AppCatSDKException(t.message ?: "setTrackingConsentAsync failed"))
      }
    }
}
