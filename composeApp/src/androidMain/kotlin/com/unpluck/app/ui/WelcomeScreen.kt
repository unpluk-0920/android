package com.unpluck.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val tabs = listOf(
        WelcomeTab("Instant Focus", "Flip the switch on your case to instantly replace your distracting home screen with a minimalist, focused UI.", Icons.Rounded.ToggleOn),
        WelcomeTab("Use Your Launcher", "When you're not in Focus Mode, Unpluk works as a proxy, seamlessly sending you to your phone's original home screen.", Icons.Rounded.Widgets),
        WelcomeTab("Private & Secure", "Unpluk only needs permissions to function. Your data stays on your device, always.", Icons.Rounded.Shield)
    )

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(50.dp)
            ) {
                Text("Continue to Permissions")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Logo and Description
            Spacer(modifier = Modifier.height(64.dp))
//            Icon(
//                painter = painterResource(id = R.drawable.ic_launcher_background),
//                contentDescription = "Unpluk Logo",
//                modifier = Modifier.size(80.dp),
//                tint = MaterialTheme.colorScheme.primary
//            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Welcome to Unpluk",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "The smart way to reclaim your focus.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 2. Tabs for Features
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                FeatureInfoCard(tab = tabs[page])
            }
        }
    }
}

@Composable
private fun FeatureInfoCard(tab: WelcomeTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            tab.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            tab.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

private data class WelcomeTab(
    val title: String,
    val description: String,
    val icon: ImageVector
)