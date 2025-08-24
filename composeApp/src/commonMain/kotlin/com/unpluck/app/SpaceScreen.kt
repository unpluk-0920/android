import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Add this new function in SpaceScreen.kt

@Composable
fun LauncherSelectionScreen(
    // This will be a list of LauncherInfo objects, but we can't use that type in commonMain.
    // So we'll pass the data as simple lists.
    labels: List<String>,
    onLauncherSelected: (Int) -> Unit
) {
    // We need to add implementations for Image composable to render the icon
    // For now, let's just show the names.
    Column (
        modifier = Modifier.fillMaxSize().background(Color.DarkGray).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Your Preferred Launcher", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn {
            items(labels.size) { index ->
                Button(
                    onClick = { onLauncherSelected(index) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(labels[index], fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun SpaceScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // A simple black background
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "unpluck space",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}