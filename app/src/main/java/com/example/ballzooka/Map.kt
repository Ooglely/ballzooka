package com.example.ballzooka

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.*


@OptIn(ExperimentalPermissionsApi::class)
@RequiresPermission(allOf = [
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT
])
@Composable
fun MapDisplay(viewModel: BallzookaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val userLocationProvider = rememberUserLocationProvider()
    val locationUpdates = userLocationProvider.getLocationUpdates()
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var cannonLocation = LatLng(currentLocation?.latitude ?: 0.0, currentLocation?.longitude ?: 0.0)
    val cannonBearing = 135.0f
    var userLocation: LatLng = LatLng(37.95613795574523, -91.77284471474142)

    val userMarkerState = rememberUpdatedMarkerState(position = userLocation)

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    )

    // 37.956547, -91.772350
    val markerPosition = currentLocation?.let {
        LatLng(it.latitude, it.longitude)
    } ?: LatLng(37.956547, -91.772350)



    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(markerPosition, 20f)
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            userLocationProvider.getLocationUpdates().collect {
                    location -> currentLocation = location
            }
        }
    }

    // Default map properties
    val mapProperties = MapProperties(
        mapType = MapType.SATELLITE
    )

    val conePoints = remember(cannonLocation, cannonBearing) {
        createConePolygon(cannonLocation, cannonBearing, 30f, 50.0)
    }


    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = true
            )
        ) {
            Marker(
                state = userMarkerState,
                title = "User Location",
                snippet = "(${userLocation?.latitude}, ${userLocation?.longitude})"
            )
            if (currentLocation != null && uiState.currentState != AppState.START) {
                Marker(
                    state = MarkerState(position = markerPosition),
                    title = "Cannon Location",
                    snippet = "(${currentLocation?.latitude}, ${currentLocation?.longitude})"
                )
                Circle(
                    center = markerPosition, // Change to cannon position
                    radius = 50.0,
                    fillColor = Color(red = 255, green = 255, blue = 158, alpha = 100)
                )
                Polygon(
                    points = conePoints,
                    fillColor = Color(red = 200, green = 0, blue = 0, alpha = 100),
                    strokeColor = Color(red = 255, green = 0, blue = 0, alpha = 255),
                    strokeWidth = 2f
                )
            }
        }

        if (uiState.currentState != AppState.START) {
            if (permissionsState.allPermissionsGranted) {
                Text(
                    text = "Cannon: (${currentLocation?.latitude}, ${currentLocation?.longitude})",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 16.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            } else {
                Button(
                    onClick = {permissionsState.launchMultiplePermissionRequest()},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 16.dp)
                ) {
                    Text(text = "Request Permissions")
                }
            }

            FloatingActionButton(
                onClick = {},
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(56.dp)
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Go to my location"
                )
            }
        }

        if (uiState.currentState == AppState.SAFETY) {
            AlertDialog(
                icon = {
                    Icon(Icons.Filled.PersonalInjury, contentDescription = "Example Icon")
                },
                title = {
                    Text(text = "Safety Check")
                },
                text = {
                    Text(
                        text = "An object was detected in front of the cannon. " +
                                "The cannon will spin down now.\n\n" +
                                "The cannon will not allow for firing if there is something in the way to protect users. " +
                                "This dialog will close once the obstruction is cleared."
                    )
                },
                onDismissRequest = {},
                confirmButton = {},
                dismissButton = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MapPreview() {
//    MapDisplay()
}

@Composable
fun rememberUserLocationProvider(): UserLocation {
    val context = LocalContext.current
    return remember { UserLocation(context) }
}

class UserLocation(val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getLocationUpdates(interval: Long = 5000): Flow<Location> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            interval
        ).apply {
            setMinUpdateIntervalMillis(interval)
            setWaitForAccurateLocation(true)
        }.build()

        // Define the new location callback, send new location through the flow
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                p0.lastLocation?.let {
                    location -> trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

fun createConePolygon(
    center: LatLng,
    bearing: Float,
    coneAngle: Float,
    radiusMeters: Double,
    segments: Int = 20
): List<LatLng> {
    val points = mutableListOf<LatLng>()

    // Start at the center point (tip of the cone)
    points.add(center)

    // Calculate the arc from (bearing - coneAngle/2) to (bearing + coneAngle/2)
    val startAngle = bearing - coneAngle / 2
    val endAngle = bearing + coneAngle / 2
    val angleStep = coneAngle / segments

    for (i in 0..segments) {
        val angle = startAngle + i * angleStep
        val point = offsetLatLng(center, radiusMeters, angle.toDouble())
        points.add(point)
    }

    // Close back to center
    points.add(center)

    return points
}

fun offsetLatLng(center: LatLng, distanceMeters: Double, bearingDegrees: Double): LatLng {
    val earthRadius = 6371000.0

    val bearingRad = bearingDegrees.toRadians()
    val lat1 = center.latitude.toRadians()
    val lon1 = center.longitude.toRadians()

    val angularDistance = distanceMeters / earthRadius

    val lat2 = asin(
        sin(lat1) * cos(angularDistance) +
                cos(lat1) * sin(angularDistance) * cos(bearingRad)
    )

    val lon2 = lon1 + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )

    return LatLng(lat2.toDegrees(), lon2.toDegrees())
}

private fun Double.toRadians() = this * PI / 180.0
private fun Double.toDegrees() = this * 180.0 / PI