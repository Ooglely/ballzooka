package com.example.ballzooka

import android.bluetooth.BluetoothGattCharacteristic
import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.lang.IllegalArgumentException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class LaunchConditions(
    val timeOfFlight: Double,
    val velocity: Double
)

// Wind class to hold wind data
// Not implemented yet for correction
class Wind(
    val heading: Double = 0.0,
    val speed: Double = 0.0,
) {
    fun mitigate(target: LatLng): LatLng {
        return target
    }
}

class ArmParams(
    val yaw: Double,
    val pitch: Double,
    val speed: Double
)

fun findLaunchVelocity(distance: Float, pitch: Double): LaunchConditions {
    // All this math is taken from the Tentative Code/Ballistics Math Model code in the drive

    val ballMass = 0.058 // in kg
    val g = 9.807 // gravity
    val Cd = 0.56 // drag coefficent of the ball
    val rho = 1.215 // air density
    val ballArea = 0.003425 // cross-sectional area of the ball (m^2)
    val theta = pitch * PI / 180.0 // convert pitch to radians

    // add wind correction here

    // narrow down possible velocities by taking the middle of the bounds
    var lowerBound = 1.0
    var upperBound = 200.0
    var timeOfFlight = 0.0
    for (iter in 0..100) {
        val v0 = 0.5 * (lowerBound + upperBound)
        // split velocity into the two components
        var vx = v0 * cos(theta)
        var vy = v0 * sin(theta)

        var x = 0.0; // ball x
        var y = 0.0; // ball y
        var t = 0.0; // ball t

        // eulers method
        while (y >= 0.0)
        {
            val timeStep = 0.001
            val speed = sqrt(vx.pow(2) + vy.pow(2)) // magnitude of velocity
            val drag = 0.5 * Cd * rho * ballArea * speed // drag magnitude (linear NASA form)

            // Acceleration components:
            // Drag always opposes motion → multiply by velocity direction
            val ax = -(drag / ballMass) * vx
            val ay = -(drag / ballMass) * vy - g        // gravity acts downward always

            // Velocity updates
            vx += ax * timeStep;
            vy += ay * timeStep;

            // Position updates
            x += vx * timeStep;
            y += vy * timeStep;

            t += timeStep;

            // Early cutoff if ridiculously overshooting
            if (x > distance + 5.0)
                break
        }

        // check if overshot or undershot
        if (x < distance) {
            // Undershot -> velocity too low
            lowerBound = v0;
        } else {
            // Overshot -> velocity too high
            upperBound = v0;
            timeOfFlight = t; // time-of-flight associated with overshoot side
        }
    }

    return LaunchConditions(timeOfFlight, (upperBound + lowerBound) / 2)
}

fun calculateAngles(cannon: LatLng, target: LatLng, pitch: Double = 45.0): ArmParams {

    // add wind correction here

    val distanceResults = FloatArray(3)
    Location.distanceBetween(cannon.latitude, cannon.longitude, target.latitude, target.longitude, distanceResults)
    // calculate altitude difference?
    val distance = distanceResults[0]
    val yaw = distanceResults[1]
    if (distance > 100.0) {
        throw IllegalArgumentException("Distance between cannon and target is too large")
    }

    val launchConditions = findLaunchVelocity(distance, pitch)
    val rpm = (launchConditions.velocity * 60 * 3.28 * 12) / (2 * PI * 3)

    if (rpm > 5000) {
        throw RuntimeException("The RPM required (${"%.2f".format(rpm)}) is too high. Please lower your angle or select a closer point.")
    }

    Log.d("BallzookaCalculations", "Distance: ${distanceResults[0]}")
    Log.d("BallzookaCalculations", "Time of flight: ${launchConditions.timeOfFlight}s")
    Log.d("BallzookaCalculations", "Velocity: ${launchConditions.velocity}m/s")
    Log.d("BallzookaCalculations", "RPM: $rpm")

    return ArmParams(yaw.toDouble(), pitch, rpm)
}