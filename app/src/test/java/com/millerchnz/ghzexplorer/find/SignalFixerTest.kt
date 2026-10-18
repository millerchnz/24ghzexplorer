package com.millerchnz.ghzexplorer.find

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalFixerTest {
    @Test
    fun oneReadingCentersOnThePhoneWithAWideCircle() {
        val estimate = SignalFixer.estimate(
            listOf(SignalFix(-36.8, 174.7, rssiDbm = -70, accuracyMeters = 5.0)),
        ) ?: error("expected an estimate")
        assertEquals(-36.8, estimate.latitude, 1e-6)
        assertEquals(174.7, estimate.longitude, 1e-6)
        val range = SignalFixer.rangeMeters(-70)
        assertEquals(range + 5.0, estimate.radiusMeters, 0.2)
        assertTrue(estimate.radiusMeters > 15.0)
    }

    @Test
    fun aWalkThatGetsLouderPullsTheMarkerThatWay() {
        val track = FixTrack()
        track.add(SignalFix(0.0, 0.0, rssiDbm = -80, accuracyMeters = 5.0))
        track.add(SignalFix(0.0002, 0.0, rssiDbm = -75, accuracyMeters = 5.0))
        track.add(SignalFix(0.0004, 0.0, rssiDbm = -65, accuracyMeters = 5.0))
        val estimate = track.estimate ?: error("expected an estimate")
        assertTrue(estimate.latitude > 0.00025)
        assertEquals(3, track.samples.size)
    }

    @Test
    fun readingsFromAroundTheDeviceShrinkTheCircle() {
        val meters = 30.0 / 111_320.0
        val estimate = SignalFixer.estimate(
            listOf(
                SignalFix(meters, 0.0, -40, 4.0),
                SignalFix(-meters, 0.0, -40, 4.0),
                SignalFix(0.0, meters, -40, 4.0),
                SignalFix(0.0, -meters, -40, 4.0),
            ),
        ) ?: error("expected an estimate")
        assertEquals(0.0, estimate.latitude, 0.00005)
        assertEquals(0.0, estimate.longitude, 0.00005)
        assertEquals(SignalFixer.MIN_RADIUS_METERS, estimate.radiusMeters, 0.01)
    }

    @Test
    fun standingStillDoesNotStackReadings() {
        val track = FixTrack()
        track.add(SignalFix(0.0, 0.0, -70, 5.0))
        track.add(SignalFix(0.00001, 0.0, -60, 5.0))
        assertEquals(1, track.samples.size)
        assertEquals(-60, track.samples.single().rssiDbm)
    }

    @Test
    fun aDeviceAppearsAfterThreePlacesAndLeavesWhenSamplesStop() {
        val survey = LiveSurvey("wifi")
        val device = SurveyDevice("ap", "Home", "aa", emptyList(), rssiDbm = -60, stamp = 1)
        assertTrue(survey.update(0.0, 0.0, 5.0, listOf(device)).isEmpty())
        assertTrue(survey.update(0.0002, 0.0, 5.0, listOf(device.copy(stamp = 2))).isEmpty())
        assertEquals(
            listOf("ap"),
            survey.update(0.0004, 0.0, 5.0, listOf(device.copy(stamp = 3))).map { it.key },
        )
        assertTrue(survey.update(0.0004, 0.0, 5.0, emptyList()).isEmpty())
    }

    @Test
    fun aFifthPlaceReplacesTheOldest() {
        val track = FixTrack()
        repeat(5) { index ->
            track.add(SignalFix(index * 0.0002, 0.0, rssiDbm = -70, accuracyMeters = 5.0))
        }
        assertEquals(4, track.samples.size)
        assertEquals(0.0002, track.samples.first().latitude, 1e-9)
        assertEquals(0.0008, track.samples.last().latitude, 1e-9)
        assertTrue(track.estimate != null)
    }

    @Test
    fun theFirstPlaceStaysWhenALaterPlaceIsHeard() {
        val log = SeenLog()
        log.note("ap", 0.0, 0.0, -70)
        log.note("ap", 0.0002, 0.0, -50)
        val span = log.span("ap") ?: error("expected places")
        assertEquals(-70, span.first.rssiDbm)
        assertEquals(0.0, span.first.latitude, 1e-9)
        assertEquals(-50, span.last.rssiDbm)
        assertEquals(0.0002, span.last.latitude, 1e-9)
    }
}
