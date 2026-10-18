package com.millerchnz.ghzexplorer.find

import com.millerchnz.ghzexplorer.data.ApiField

/** A device the live map can draw. The samples are only the ones still being heard. */
data class DevicePlot(
    val kind: String,
    val key: String,
    val title: String,
    val subtitle: String,
    val fields: List<ApiField>,
    val samples: List<SignalFix>,
) {
    val estimate: DeviceEstimate?
        get() = SignalFixer.estimate(samples)
}
