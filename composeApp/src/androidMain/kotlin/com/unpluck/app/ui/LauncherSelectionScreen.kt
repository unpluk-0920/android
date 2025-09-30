package com.unpluck.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unpluck.app.MainViewModel
import com.unpluck.app.R
import com.unpluck.app.defs.LauncherType
import com.unpluck.app.ui.theme.GradientEnd
import com.unpluck.app.ui.theme.GradientMid
import com.unpluck.app.ui.theme.GradientMid2
import com.unpluck.app.ui.theme.GradientStart
import kotlinx.coroutines.delay

data class LauncherOption(
    val type: LauncherType,
    val title: String,
    val description: String,
    val imageResId: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSelectionScreen(
    viewModel: MainViewModel,
    onLauncherSelected: () -> Unit
) {
    val launcherOptions = listOf(
        LauncherOption(LauncherType.ORIGINAL_PROXY, "Original Launcher", "Keep your phone's default home screen.", R.drawable.home),
        LauncherOption(LauncherType.COMING_SOON, "Search based launcher", "coming soon", R.drawable.home),
        LauncherOption(LauncherType.COMING_SOON, "Minimalism based launcher", "Coming Soon", R.drawable.home)
    )

    val pagerState = rememberPagerState(pageCount = { launcherOptions.size })
    val gradientColors = listOf(GradientStart, GradientMid, GradientMid2, GradientEnd)

    Scaffold(
        modifier = Modifier.background(
            brush = Brush.linearGradient(colors = gradientColors)
        ),
        containerColor = Color.Transparent
        ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Choose Your Launcher", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            val context = LocalContext.current
            HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 40.dp)) { page ->
                val isDownloadButtonEnabled = launcherOptions[page].type == LauncherType.COMING_SOON
                LauncherCard(
                    option = launcherOptions[page],
                    onSelect = {
                        viewModel.saveLauncherChoice(context, launcherOptions[page].type)
                        onLauncherSelected()
                    },
                    !isDownloadButtonEnabled,
                )
            }
        }
    }
}

@Composable
private fun LauncherCard(option: LauncherOption, onSelect: () -> Unit, isDownloadDisabled: Boolean) {
    var downloadState by remember { mutableFloatStateOf(0f) } // 0f = not started, 1f = complete
    var isDownloading by remember { mutableStateOf(false) }

    // Simulate download when button is clicked
    LaunchedEffect(isDownloading) {
        if (isDownloading) {
            for (i in 1..100) {
                downloadState = i / 100f
                delay(20) // Simulate network delay
            }
            onSelect() // When download is "complete", trigger the selection
        }
    }

    Card(
        modifier = Modifier.padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painter = painterResource(id = option.imageResId), contentDescription = option.title, modifier = Modifier.height(200.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(option.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(option.description, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(24.dp))

            if (isDownloadDisabled) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { downloadState },
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 3.dp,
                    )
                    IconButton(
                        onClick = { if (!isDownloading) isDownloading = true },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download and Select")
                    }
                }
            }
        }
    }
}