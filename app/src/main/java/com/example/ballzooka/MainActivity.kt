package com.example.ballzooka

import android.annotation.SuppressLint
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Start scanning for Ballzooka
            val viewModel: BallzookaViewModel by viewModels()
            viewModel.findAndConnect()

            Box(modifier = Modifier.systemBarsPadding()) {
                Column {
                    Toolbar()
                    StateButtons(viewModel)
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
            for (state in AppState.entries) {
                Button(
                    onClick = {
                        viewModel.changeState(state)
                    },
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(state.name)
                }
            }
            Button(onClick = {
                var data = ByteArray(1)
                data[0] = 1
                viewModel.changeValue(data)
            }) {
                Text("Test Command")
            }
        }
    }
}