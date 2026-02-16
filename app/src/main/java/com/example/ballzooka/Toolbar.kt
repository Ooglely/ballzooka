package com.example.ballzooka

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel


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

    Surface(color = Color.Black, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp).height(height = 75.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ballzooka",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            )
            if (uiState.currentState != AppState.START) {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(text = "Wind: 7 mph 64°", color = Color.White, fontSize = 24.sp)
                    Text(text = "Direction: 125°", color = Color.White, fontSize = 24.sp)
                }
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = "State: ${uiState.currentState.name}",
                        color = Color.White,
                        fontSize = 24.sp
                    )
                    Text(text = "Motors: 0 RPM", color = Color.White, fontSize = 24.sp)
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

    Row(
        modifier = Modifier.padding(vertical = 8.dp).height(height = 75.dp),
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
                Button(onClick = {}, modifier = Modifier.height(48.dp), colors = NormalButtonColors) {
                    Text("Lock", fontSize = 24.sp)
                }
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
                Text(
                    text = "Connection State: ${uiState.connectionStatus.name}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    fontSize = 24.sp
                )
            }
            AppState.AIMING -> {
                Text(text = "Cannon is aiming towards location...", color = Color.White, fontSize = 24.sp)
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = NormalButtonColors) {
                    Text("Lock", fontSize = 24.sp)
                }
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
            }
            AppState.ARMED -> {
                Button(onClick = {}, modifier = Modifier.height(48.dp), colors = NormalButtonColors) {
                    Text("Disarm", fontSize = 24.sp)
                }
                Button(onClick = {}, modifier = Modifier.height(48.dp), colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
            }
            AppState.SAFETY -> {
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = NormalButtonColors) {
                    Text("Lock", fontSize = 24.sp)
                }
                Button(onClick = {}, modifier = Modifier.height(48.dp), enabled = false, colors = DangerousButtonColors) {
                    Text("Fire", fontSize = 24.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ToolbarPreview() {
    Toolbar()
}