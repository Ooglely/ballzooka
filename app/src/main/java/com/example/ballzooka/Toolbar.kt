package com.example.ballzooka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.application
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import org.intellij.lang.annotations.JdkConstants


val NormalButtonColors = ButtonColors(
    containerColor = Color(10, 50, 0, 255),
    contentColor = Color.White,
    disabledContainerColor = Color.Gray,
    disabledContentColor = Color.White
)
val DangerousButtonColors = ButtonColors(
    containerColor = Color.Red,
    contentColor = Color.White,
    disabledContainerColor = Color.Gray,
    disabledContentColor = Color.White
)

@Composable
fun Toolbar(viewModel: BallzookaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    Surface(color = Color.Black, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp).height(height = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ballzooka",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                )
                Text(
                    text = "State: ${uiState.currentState.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    fontSize = 8.sp
                )
            }
            if (uiState.currentState != AppState.START) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Pitch: ${"%.2f".format(telemetry.pitch)}°",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Yaw: ${"%.2f".format(telemetry.heading)}°",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(text = "Wind: 7 mph 69°", color = Color.White, fontSize = 16.sp)
                    Text(
                        text = "Motors: ${telemetry.rpm} RPM",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            StateControls()
        }
    }
}

@Composable
fun StateControls(viewModel: BallzookaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp).height(height = 60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (uiState.currentState) {
            AppState.START -> {
                Text(
                    text = "Connection State: ${uiState.connectionStatus.name}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    fontSize = 24.sp
                )
                Button(onClick = {}, colors = NormalButtonColors) {
                    Text("Help", fontSize = 24.sp)
                }
            }
            AppState.IDLE -> {
                Button(onClick = {
                    if (telemetry.selection != LatLng(0.0, 0.0)) {

                        try {
                            val angles = calculateAngles(LatLng(telemetry.latitude, telemetry.longitude), telemetry.selection, telemetry.desiredPitch)
                            viewModel.arm(angles)
                            viewModel.changeState(AppState.AIMING)
                        } catch (_: IllegalArgumentException) {
                            viewModel.addEvent("The distance between the cannon and target is too large (>100m). Please select a new location.")
                        } catch (e: RuntimeException) {
                            viewModel.addEvent(e.message.toString())
                        }
                    }
                }, modifier = Modifier.height(48.dp), colors = NormalButtonColors) {
                    Text("Lock", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
            }
            AppState.AIMING -> {
                Text(text = "Cannon is aiming towards location...", color = Color.White, fontSize = 20.sp)
            }
            AppState.ARMED -> {
                Button(onClick = {
                    viewModel.disarm()
                    viewModel.changeState(AppState.IDLE)
                 }, modifier = Modifier.height(48.dp), colors = NormalButtonColors) {
                    Text("Disarm", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {viewModel.messenger.send(viewModel.messenger.characteristics!!.commandFlywheelRPM, byteArrayOf(1.toByte()))}, modifier = Modifier.height(48.dp), colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
            }
            AppState.SAFETY -> {
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = NormalButtonColors) {
                    Text("Lock", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun PitchSlider(viewModel: BallzookaViewModel = viewModel()) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxHeight().background(Color.Black)) {
        Column(modifier = Modifier.width(50.dp).background(Color.Black).padding(all = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Pitch", color = Color.White, fontSize = 12.sp)
            Slider(
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight - 40,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxHeight,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width, 0)
                        }
                    }
                    .padding(horizontal = 10.dp),
                value = telemetry.desiredPitch.toFloat(),
                valueRange = 10f..80f,
                onValueChange = {viewModel.updateDesiredPitch(it.toDouble())},
                colors = SliderColors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Cyan,
                    inactiveTrackColor = Color.White,

                    // the rest doesn't matter but is needed
                    activeTickColor = Color.Blue,
                    inactiveTickColor = Color.White,
                    disabledThumbColor = Color.White,
                    disabledActiveTrackColor = Color.White,
                    disabledInactiveTrackColor = Color.White,
                    disabledActiveTickColor = Color.White,
                    disabledInactiveTickColor = Color.White
                )
            )
            Text(text = "%.2f°".format(telemetry.desiredPitch), color = Color.White, fontSize = 9.sp)

        }
    }
}


@Preview(showBackground = true)
@Composable
fun ToolbarPreview() {
    Toolbar()
}