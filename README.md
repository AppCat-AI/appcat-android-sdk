# AppCat Android SDK

Track events and attribute installs across Meta, TikTok, Google Ads, and Apple Search Ads from native Android apps. Resolve deferred deep links to route users to the right content after install.

## Overview

- Track standard and custom events across ad platforms
- Track revenue events with currency and value
- Resolve deferred deep links after install to route users to the right screen
- Retrieve the AppCat device ID and attribution data
- Both callback-based and Kotlin coroutine APIs available

## Features (with examples)

### Getting Started

Add your credentials to `local.properties` or `buildConfigField` in `build.gradle`, then reference them via `BuildConfig`. `appId` is optional if the API key can resolve it from AppCat.

#### 1. Init

Initialize the SDK with your credentials. This automatically creates the attribution profile and resolves any deferred deep links.

```kotlin
import com.appcat.sdk.AppCat

AppCat.configureAsync(
    applicationContext,
    apiKey = BuildConfig.APPCAT_API_KEY,
    appId = BuildConfig.APPCAT_APP_ID
)
```

#### 2. Deep Links

`configureAsync()` returns a response with deep link params from the matched ad click URL. Use this to route users to the right screen on first open.

```kotlin
val response = AppCat.configureAsync(
    applicationContext,
    apiKey = BuildConfig.APPCAT_API_KEY,
    appId = BuildConfig.APPCAT_APP_ID
)
response.deepLinkParams?.let { params ->
    // route user based on params
}
```

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `deepLinkParams` | `Map<String, String>?` | Query params from the matched ad click URL, or `null` if no match |
| `geo` | `AppCatGeoResponse?` | Geo data. e.g. `city = "San Francisco"`, `country = "US"`, `state = "CA"` |

### Event Tracking

```kotlin
AppCat.sendEvent("Purchase", mapOf("item" to "premium_plan"))
AppCat.sendEvent("ViewContent", mapOf("category" to "shoes", "productId" to "SKU-100"))
AppCat.sendEvent("CompleteRegistration")
```

### Revenue Tracking

```kotlin
AppCat.sendEvent("Purchase", mapOf(
    "item" to "annual_plan",
    "value" to 49.99,
    "currency" to "USD"
))
```

### Tracking Consent

```kotlin
AppCat.setTrackingConsent(granted = false)
// or
AppCat.setTrackingConsentAsync(granted = false)
```

Call this after ATT-equivalent, GDPR, or an in-app privacy choice. When consent is denied, AppCat avoids forwarding certain PII fields to ad networks on your behalf.

### Installation ID and Attribution

```kotlin
val deviceId = AppCat.getAppCatId()
val attribution = AppCat.getAttribution()
```

## Installation

### Gradle (JitPack)

The SDK is published via JitPack. The closed-source core AAR is embedded inside the published artifact, so a single dependency declaration is all that is needed.

In your **root** `settings.gradle` (or `build.gradle`):

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

In your **app module** `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.AppCat-AI:appcat-android-sdk:0.1.0'
}
```

### Gradle (Maven Central)

Maven Central publication is set up in parallel. Once available:

```groovy
dependencies {
    implementation 'com.appcat:appcat-android-sdk:0.1.0'
}
```

## Platform Configuration

| Requirement | Minimum Version |
|-------------|----------------|
| Android | 5.0+ (API 21) |
| Kotlin | 1.9+ |
| Java | 17+ |

## Quick Start

```kotlin
import android.app.Application
import android.util.Log
import com.appcat.sdk.AppCat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = AppCat.configureAsync(
                    applicationContext,
                    apiKey = BuildConfig.APPCAT_API_KEY,
                    appId = BuildConfig.APPCAT_APP_ID
                )
                response.deepLinkParams?.let { params ->
                    // Route the user based on params.
                }
                response.geo?.let { geo ->
                    // Use geo if your app needs it.
                }
            } catch (e: Exception) {
                Log.e("AppCat", "Error: ${e.message}")
            }
        }
    }

    // Identify — call post-login when PII becomes available
    fun onLogin(userId: String, email: String) {
        CoroutineScope(Dispatchers.Main).launch {
            AppCat.identifyAsync(mapOf(
                "userId" to userId,
                "email" to email,
                // "revenueCatIds" to listOf("rc_user_123"),
            ))
        }
    }

    fun onPurchase() {
        AppCat.sendEvent("Purchase", mapOf(
            "item" to "premium_plan",
            "value" to 9.99,
            "currency" to "USD"
        ))
    }
}
```

## API

### `AppCat.configure(context, apiKey, appId?, options?, callback?)`

Initialize the SDK and create the attribution profile. Automatically resolves deferred deep links and returns any matched query params via callback. Must be called before any other method.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `context` | `Context` | Yes | Android application context |
| `apiKey` | `String` | Yes | API key for your AppCat project |
| `appId` | `String` | No | Your AppCat application ID (resolved from API key if omitted) |
| `options` | `Map<String, Any?>` | No | Additional configuration options |
| `callback` | `(Result<AppCatInitResponse>) -> Unit` | No | Called with deep link params and geo, or an error |

**Returns:** `Unit`

**Coroutine variant:** `suspend configureAsync(context, apiKey, appId?, options?): AppCatInitResponse`

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `deepLinkParams` | `Map<String, String>?` | Query params from the matched ad click URL, or `null` if no match |
| `geo` | `AppCatGeoResponse?` | Geo data. e.g. `city = "San Francisco"`, `country = "US"`, `state = "CA"` |

**Example:**

```kotlin
AppCat.configure(context, BuildConfig.APPCAT_API_KEY, BuildConfig.APPCAT_APP_ID) { result ->
    result.onSuccess { response ->
        response.deepLinkParams?.let { params ->
            Log.d("AppCat", "Deep link: $params")
        }
    }
    result.onFailure { Log.e("AppCat", "Failed: ${it.message}") }
}
```

---

### `AppCat.identify(data, callback?)`

Enrich the user profile with additional information. Call after login, signup, or whenever new user data is available.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `data` | `Map<String, Any?>` | Yes | Map with user data (`userId`, `email`, `phone`, `name`, `geo`, `revenueCatIds`, `customAttributes`) |
| `data["revenueCatIds"]` | `List<String>` | No | RevenueCat app user IDs to associate with this profile |
| `callback` | `(Result<AppCatIdentifyResponse>) -> Unit` | No | Called with the identify response |

**Returns:** `Unit`

**Coroutine variant:** `suspend identifyAsync(data): AppCatIdentifyResponse`

**Response:**

| Field | Type | Description |
|-------|------|-------------|
| `geo` | `AppCatGeoResponse?` | Geo data. e.g. `city = "San Francisco"`, `country = "US"`, `state = "CA"` |
| `deepLinkParams` | `Map<String, String>?` | Deep link params from the attribution profile |

**Example:**

```kotlin
val result = AppCat.identifyAsync(mapOf(
    "userId" to "user_123",
    "email" to "user@example.com",
    "name" to "Jane Smith",
    "revenueCatIds" to listOf("rc_user_123")
))
```

---

### `AppCat.sendEvent(eventName, params?)`

Track a conversion event. This method never throws.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `eventName` | `String` | Yes | Event name (see available events below) |
| `params` | `Map<String, Any?>?` | No | Event parameters including reserved keys (`eventId`, `value`, `currency`, `testEventCode`) and custom data |

**Returns:** `Unit`

**Example:**

```kotlin
AppCat.sendEvent("Subscribe", mapOf(
    "plan" to "annual",
    "value" to 99.99,
    "currency" to "USD",
    "eventId" to "order-abc-123"
))
```

---

### `AppCat.getAttribution()`

Get cached attribution data. Returns `null` if neither `configure()` nor `identify()` has been called.

**Returns:** `Map<String, Any?>?`

---

### `AppCat.getDeviceContext()`

Get cached device context.

**Returns:** `Map<String, Any?>?`

---

### `AppCat.getAppCatId()`

Get the stable AppCat device identifier.

**Returns:** `String?`

---

### `AppCat.isDisabled()`

Check if the SDK has been remotely disabled.

**Returns:** `Boolean`

---

### `AppCat.isInitialized()`

Check whether the SDK has been configured.

**Returns:** `Boolean`

---

### `AppCat.setLogLevel(level)`

Set log verbosity at runtime.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `level` | `AppCatLogLevel` | Yes | `DEBUG`, `INFO`, `WARN`, or `ERROR` |

**Returns:** `Unit`

---

### `AppCat.setTrackingConsent(granted, callback?)`

Record the user's tracking-consent choice.

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `granted` | `Boolean` | Yes | Whether tracking consent is granted |
| `callback` | `(Result<Unit>) -> Unit` | No | Called when the server accepts or rejects the update |

**Returns:** `Unit`

**Coroutine variant:** `suspend setTrackingConsentAsync(granted): Unit`

## ProGuard / R8

The SDK ships consumer rules that keep the public wrapper and core classes:

```proguard
-keep class com.appcat.sdk.** { *; }
-keep class com.appcat.core.** { *; }
```

## Privacy

Do not log raw attribution, deep-link params, email, or phone in production. Call `setTrackingConsent(false)` when the user denies tracking consent.

## Available Event Types

| Event Name | Description |
|------------|-------------|
| `MobileAppInstall` | App installed |
| `ViewContent` | User viewed content |
| `AddToCart` | Item added to cart |
| `InitiateCheckout` | Checkout started |
| `StartTrial` | Free trial started |
| `Subscribe` | Subscription started |
| `Purchase` | Purchase completed |
| `CompleteRegistration` | Registration completed |
| `Search` | Search performed |

Custom event names are also supported as any string value.

## License

MIT -- see [LICENSE](./LICENSE) for details.
