package com.millerchnz.ghzexplorer.ui.find

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.find.DeviceEstimate
import com.millerchnz.ghzexplorer.find.FixTrack
import com.millerchnz.ghzexplorer.find.SignalFix
import com.millerchnz.ghzexplorer.find.SignalReading

class FindSession : ViewModel() {
    private val track = FixTrack()
    private var stamp = Long.MIN_VALUE

    var userLatitude by mutableStateOf<Double?>(null)
        private set
    var userLongitude by mutableStateOf<Double?>(null)
        private set
    var hearing by mutableStateOf(false)
        private set
    var lastRssi by mutableStateOf<Int?>(null)
        private set
    var estimate by mutableStateOf<DeviceEstimate?>(null)
        private set
    var trail by mutableStateOf<List<SignalFix>>(emptyList())
        private set
    var sampleCount by mutableIntStateOf(0)
        private set

    fun onFix(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        deviceKey: String,
        reading: SignalReading?,
    ) {
        userLatitude = latitude
        userLongitude = longitude
        if (reading == null) {
            hearing = false
            return
        }
        hearing = true
        lastRssi = reading.rssiDbm
        if (reading.stamp == stamp) return
        stamp = reading.stamp
        track.add(SignalFix(latitude, longitude, reading.rssiDbm, accuracyMeters))
        AppGraph.seen.note(deviceKey, latitude, longitude, reading.rssiDbm)
        estimate = track.estimate
        trail = track.samples.toList()
        sampleCount = track.samples.size
    }
}
