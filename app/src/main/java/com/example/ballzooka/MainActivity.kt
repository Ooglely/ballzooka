package com.example.ballzooka

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_ADVERTISE
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Ask for permissions
            val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    1
                )
            }

            // Start scanning for Ballzooka
            val viewModel: BallzookaViewModel by viewModels()
//            viewModel.findAndConnect()

            Box(modifier = Modifier.systemBarsPadding()) {
                Column {
                    Toolbar()
//                    StateButtons(viewModel)
                    MapDisplay()
                }
            }
        }
    }
}

@Composable
@Preview
fun AppPreview() {
    Box(modifier = Modifier.systemBarsPadding()) {
        Column {
            Toolbar()
//            MapDisplay()
        }
    }
}

// Bar with buttons to control state for debugging
@Composable
fun StateButtons(viewModel: BallzookaViewModel = viewModel()) {
    Surface(color = Color.Black, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Button(
                onClick = {
                    viewModel.send(viewModel.messenger.characteristics!!.commandLoadwheelAngle, byteArrayOf(1.toByte()))
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text("Loadwheel")
            }
            Button(
                onClick = {
                    viewModel.messenger.send(viewModel.messenger.characteristics!!.commandFlywheelRPM, byteArrayOf(1.toByte()))
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text("Flywheel")
            }
        }
    }
}