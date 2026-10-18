package com.millerchnz.ghzexplorer.find

import com.millerchnz.ghzexplorer.data.ApiField
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/** One place the phone stood, and the signal strength heard there. */
data class SignalFix(
    val latitude: Double,
    val longitude: Double,
    val rssiDbm: Int,
    val accuracyMeters: Double,
)

/** A fresh signal measurement. [stamp] stays the same until the radio reports again. */
data class SignalReading(
    val rssiDbm: Int,
    val stamp: Long,
)

data class DeviceEstimate(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
)

/**
 * Outdoor guess from signal strength while walking.
 *
 * The marker is the strength-weighted centre of the walk. The circle is wide when every
 * reading came from one side, and shrinks once the walk has heard the device from
 * different directions. Transmit power is assumed, so the circle is a range, not a survey mark.
 */
object SignalFixer {
    const val RSSI_AT_1M = -45.0
    const val PATH_LOSS_N = 2.2
    const val MIN_STEP_METERS = 8.0
    const val MIN_RADIUS_METERS = 8.0
    const val MAX_RADIUS_METERS = 800.0

    fun rangeMeters(rssiDbm: Int): Double {
        val decades = (RSSI_AT_1M - rssiDbm) / (10.0 * PATH_LOSS_N)
        return 10.0.pow(decades).coerceIn(1.0, 500.0)
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat0 = Math.toRadians((lat1 + lat2) / 2.0)
        val north = (lat2 - lat1) * METERS_PER_DEG
        val east = (lon2 - lon1) * METERS_PER_DEG * cos(lat0)
        return hypot(east, north)
    }

    fun estimate(samples: List<SignalFix>): DeviceEstimate? {
        if (samples.isEmpty()) return null
        val peak = samples.maxOf { it.rssiDbm }
        val weights = samples.map { 10.0.pow((it.rssiDbm - peak) / 10.0) }
        val weightSum = weights.sum().takeIf { it > 0.0 } ?: return null
        val lat0 = samples.first().latitude
        val lon0 = samples.first().longitude
        val latScale = METERS_PER_DEG
        val lonScale = METERS_PER_DEG * cos(Math.toRadians(lat0))
        var east = 0.0
        var north = 0.0
        samples.forEachIndexed { index, sample ->
            val w = weights[index]
            east += w * (sample.longitude - lon0) * lonScale
            north += w * (sample.latitude - lat0) * latScale
        }
        east /= weightSum
        north /= weightSum
        val latitude = lat0 + north / latScale
        val longitude = lon0 + east / lonScale.coerceAtLeast(1.0)
        val bearings = samples.mapNotNull { sample ->
            val northLeg = (sample.latitude - latitude) * latScale
            val eastLeg = (sample.longitude - longitude) * lonScale
            if (hypot(eastLeg, northLeg) < 1.0) null
            else (Math.toDegrees(atan2(eastLeg, northLeg)) + 360.0) % 360.0
        }
        val uncovered = largestGapDegrees(bearings) / 360.0
        val loudest = samples.maxBy { it.rssiDbm }
        val radius = (rangeMeters(peak) * uncovered + loudest.accuracyMeters.coerceAtLeast(0.0))
            .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        return DeviceEstimate(latitude, longitude, radius)
    }

    fun largestGapDegrees(bearings: List<Double>): Double {
        if (bearings.size < 2) return 360.0
        val sorted = bearings.map { (it % 360.0 + 360.0) % 360.0 }.sorted()
        var gap = sorted.first() + 360.0 - sorted.last()
        for (index in 1 until sorted.size) {
            gap = max(gap, sorted[index] - sorted[index - 1])
        }
        return gap
    }

    private const val METERS_PER_DEG = 111_320.0
}

/** Readings taken on the walk. A new fix within [SignalFixer.MIN_STEP_METERS] replaces the last one. */
class FixTrack {
    private val points = mutableListOf<SignalFix>()
    val samples: List<SignalFix> get() = points
    var estimate: DeviceEstimate? = null
        private set

    fun add(fix: SignalFix) {
        val previous = points.lastOrNull()
        if (previous != null &&
            SignalFixer.distanceMeters(previous.latitude, previous.longitude, fix.latitude, fix.longitude) <
            SignalFixer.MIN_STEP_METERS
        ) {
            points[points.lastIndex] = fix
        } else {
            points.add(fix)
            if (points.size > MAX_SAMPLES) points.removeAt(0)
        }
        estimate = if (points.size >= MIN_SAMPLES) SignalFixer.estimate(points) else null
    }

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_SAMPLES = 4
    }
}

fun List<ApiField>.signalDbm(): Int? {
    val labels = listOf("SS-RSRP", "RSRP", "RSCP", "RSSI", "dBm", "CDMA")
    for (label in labels) {
        val value = firstOrNull { it.label == label }?.raw?.toIntOrNull() ?: continue
        if (value < 0 && value > -150) return value
    }
    return null
}
