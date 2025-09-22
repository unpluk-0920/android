// In ui/IntroPagerScreen.kt
package com.unpluck.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unpluck.app.ui.theme.GradientEnd
import com.unpluck.app.ui.theme.GradientMid
import com.unpluck.app.ui.theme.GradientMid2
import com.unpluck.app.ui.theme.GradientStart

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroPagerScreen(onGetStarted: () -> Unit) {
    val pages = listOf(
        "Welcome to Unpluk",
        "Your Personal Focus Zone",
        "Ready When You Are"
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val gradientColors = listOf(GradientStart, GradientMid, GradientMid2, GradientEnd)

    Scaffold(
        // The Scaffold no longer has a bottomBar
        modifier = Modifier.background(
            brush = Brush.linearGradient(colors = gradientColors)
        ),
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f) // Pager takes up most of the space
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // TODO: Add an icon or image for each page
                    Text(
                        pages[page],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Placeholder text for the introduction screen. Explain a key feature of the app here.",
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )

                    // CHANGE 3: The "Get Started" button is now inside the Column for the last page.
                    if (page == pages.size - 1) {
                        Spacer(modifier = Modifier.height(64.dp))
                        Button(
                            onClick = onGetStarted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Get Started")
                        }
                    }
                }
            }

            // Page Indicators now sit below the pager content
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    // CHANGE 2: This padding pushes the indicators up from behind the system navigation bar.
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(12.dp)
                    )
                }
            }
        }
    }
}