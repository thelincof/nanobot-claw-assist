package com.nanobot.client.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Colored underline for a session @handle, mirroring the Nanobot WebUI:
 * FNV-1a over the handle id -> hue, then OKLCH -> sRGB.
 */
fun sessionHandleColor(handleId: String, dark: Boolean): Color {
    var hash = 2166136261u
    for (char in handleId) {
        hash = (hash xor char.code.toUInt()) * 16777619u
    }
    val hue = (hash % 360u).toFloat()
    return oklchToColor(
        lightness = if (dark) 0.75f else 0.5f,
        chroma = if (dark) 0.11f else 0.12f,
        hueDegrees = hue,
    )
}

private fun oklchToColor(lightness: Float, chroma: Float, hueDegrees: Float): Color {
    val hue = hueDegrees * PI.toFloat() / 180f
    val a = chroma * cos(hue)
    val b = chroma * sin(hue)

    val lPrime = lightness + 0.3963377774f * a + 0.2158037573f * b
    val mPrime = lightness - 0.1055613458f * a - 0.0638541728f * b
    val sPrime = lightness - 0.0894841775f * a - 1.2914855480f * b

    val l = lPrime * lPrime * lPrime
    val m = mPrime * mPrime * mPrime
    val s = sPrime * sPrime * sPrime

    val red = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    val green = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    val blue = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

    return Color(linearToSrgb(red), linearToSrgb(green), linearToSrgb(blue), 1f)
}

private fun linearToSrgb(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) {
        12.92f * clamped
    } else {
        1.055f * clamped.pow(1f / 2.4f) - 0.055f
    }
}
