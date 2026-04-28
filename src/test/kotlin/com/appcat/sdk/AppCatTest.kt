package com.appcat.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatTest {
    @Test
    fun `log levels keep stable raw values`() {
        assertEquals(0, AppCatLogLevel.DEBUG.value)
        assertEquals(1, AppCatLogLevel.INFO.value)
        assertEquals(2, AppCatLogLevel.WARN.value)
        assertEquals(3, AppCatLogLevel.ERROR.value)
    }

    @Test
    fun `exception carries message`() {
        val ex = AppCatSDKException("something went wrong")

        assertEquals("something went wrong", ex.message)
        assertTrue(ex is Exception)
    }

    @Test
    fun `init response stores deep link and geo data`() {
        val response = AppCatInitResponse(
            deepLinkParams = mapOf("screen" to "promo", "code" to "ABC"),
            geo = AppCatGeoResponse(city = "San Francisco", country = "US", state = "CA"),
        )

        assertEquals("promo", response.deepLinkParams?.get("screen"))
        assertEquals("US", response.geo?.country)
    }

    @Test
    fun `identify response can represent empty attribution data`() {
        val response = AppCatIdentifyResponse()

        assertNull(response.geo)
        assertNull(response.deepLinkParams)
    }
}
