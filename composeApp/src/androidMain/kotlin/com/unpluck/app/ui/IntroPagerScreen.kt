// In ui/IntroPagerScreen.kt
package com.unpluck.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IntroPagerScreen(onGetStarted: () -> Unit) {
    val pages = listOf(
        "Welcome to Unpluk",
        "Your Personal Focus Zone",
        "Ready When You Are"
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button
                AnimatedVisibility(visible = pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Text("Previous") }
                }

                // Page Indicators
                Row {
                    repeat(pages.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        Box(modifier = Modifier.padding(2.dp))
                    }
                }

                // Next / Get Started Button
                Button(onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onGetStarted()
                    }
                }) {
                    Text(if (pagerState.currentPage < pages.size - 1) "Next" else "Get Started")
                }
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(padding)) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // TODO: Add an icon or image for each page
                Text(pages[page], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Placeholder text for the introduction screen. Explain a key feature of the app here.", textAlign = TextAlign.Center)
            }
        }
    }
}