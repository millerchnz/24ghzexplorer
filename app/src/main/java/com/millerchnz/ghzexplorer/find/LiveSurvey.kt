package com.millerchnz.ghzexplorer.find

import com.millerchnz.ghzexplorer.data.ApiField

/** One device the radio is hearing at this moment. */
data class SurveyDevice(
    val key: String,
    val title: String,
    val subtitle: String,
    val fields: List<ApiField>,
    val rssiDbm: Int,
    val stamp: Long,
)

/**
 * Tracks every device passed in on the latest call. A device that is left out has stopped
 * producing samples and is dropped, along with the places it had been heard.
 */
class LiveSurvey(
    private val kind: String,
    private val seen: SeenLog = SeenLog(),
) {
    private val tracks = linkedMapOf<String, FixTrack>()
    private val stamps = mutableMapOf<String, Long>()
    private val latest = mutableMapOf<String, SurveyDevice>()

    fun update(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        heard: List<SurveyDevice>,
    ): List<DevicePlot> {
        val live = heard.map { it.key }.toSet()
        tracks.keys.retainAll(live)
        stamps.keys.retainAll(live)
        latest.keys.retainAll(live)
        for (device in heard) {
            latest[device.key] = device
            val track = tracks.getOrPut(device.key) { FixTrack() }
            val previous = track.samples.lastOrNull()
            val moved = previous == null ||
                SignalFixer.distanceMeters(previous.latitude, previous.longitude, latitude, longitude) >=
                SignalFixer.MIN_STEP_METERS
            val newSample = stamps[device.key] != device.stamp
            if (moved || newSample) {
                track.add(SignalFix(latitude, longitude, device.rssiDbm, accuracyMeters))
                stamps[device.key] = device.stamp
                seen.note(device.key, latitude, longitude, device.rssiDbm)
            }
        }
        return tracks.mapNotNull { (key, track) ->
            if (track.samples.size < FixTrack.MIN_SAMPLES) return@mapNotNull null
            val device = latest[key] ?: return@mapNotNull null
            DevicePlot(kind, key, device.title, device.subtitle, device.fields, track.samples.toList())
        }
    }
}
