package uk.aprsnet.client.location

import kotlin.math.abs

/**
 * Smart-beaconing algorithm - the standard APRS adaptive beacon-rate logic.
 *
 *  - Faster movement  => more frequent beacons
 *  - Stationary       => infrequent beacons
 *  - A sharp course change ("corner pegging") triggers an extra beacon
 *
 * Call shouldBeacon() with each new fix; it returns true when a position
 * packet should be transmitted, and remembers the time/heading it did so.
 */
class SmartBeacon(
    var lowSpeedKmh: Double = 5.0,      // at/below this -> slowRate
    var highSpeedKmh: Double = 90.0,    // at/above this -> fastRate
    var slowRateSec: Int = 20 * 60,     // beacon period when slow/stopped
    var fastRateSec: Int = 2 * 60,      // beacon period at speed
    var minTurnDeg: Int = 28,           // minimum heading change to corner-peg
    var turnSlope: Int = 26,            // turn sensitivity (higher = less sensitive)
    var minBeaconSec: Int = 30          // never beacon more often than this
) {
    private var lastBeaconTime = 0L
    private var lastCourse = -1

    /** Reset internal state (e.g. when beaconing is toggled off then on). */
    fun reset() {
        lastBeaconTime = 0L
        lastCourse = -1
    }

    /**
     * Decide whether the given fix warrants a beacon transmission now.
     * @param nowMs current time in millis
     */
    fun shouldBeacon(fix: Fix, nowMs: Long): Boolean {
        val sinceLast = (nowMs - lastBeaconTime) / 1000.0

        // hard floor - never faster than minBeaconSec
        if (lastBeaconTime != 0L && sinceLast < minBeaconSec) return false

        // first beacon ever
        if (lastBeaconTime == 0L) return mark(fix, nowMs)

        // --- speed-based beacon rate ---
        val beaconPeriod = beaconPeriodFor(fix.speedKmh)
        if (sinceLast >= beaconPeriod) return mark(fix, nowMs)

        // --- corner pegging: significant heading change ---
        if (fix.course >= 0 && lastCourse >= 0 && fix.speedKmh > lowSpeedKmh) {
            var turn = abs(fix.course - lastCourse)
            if (turn > 180) turn = 360 - turn
            // turn threshold tightens with speed
            val turnThreshold = minTurnDeg + (turnSlope / fix.speedKmh)
            if (turn >= turnThreshold) return mark(fix, nowMs)
        }

        return false
    }

    /** Beacon period in seconds for the current speed (linear interpolation). */
    private fun beaconPeriodFor(speedKmh: Double): Double = when {
        speedKmh <= lowSpeedKmh -> slowRateSec.toDouble()
        speedKmh >= highSpeedKmh -> fastRateSec.toDouble()
        else -> {
            val frac = (speedKmh - lowSpeedKmh) / (highSpeedKmh - lowSpeedKmh)
            slowRateSec - frac * (slowRateSec - fastRateSec)
        }
    }

    private fun mark(fix: Fix, nowMs: Long): Boolean {
        lastBeaconTime = nowMs
        if (fix.course >= 0) lastCourse = fix.course
        return true
    }
}