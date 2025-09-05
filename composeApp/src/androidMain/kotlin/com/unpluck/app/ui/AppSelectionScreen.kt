// In ui/AppSelectionScreen.kt
package com.unpluck.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unpluck.app.MainViewModel
import com.unpluck.app.defs.AppInfo
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(viewModel: MainViewModel) {
    val allApps by viewModel.allInstalledApps
    val selectedApps by viewModel.selectedAppPackages
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Allowed Apps") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isShowingAppSelection.value = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveAppSelection(context) }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (allApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(allApps) { app ->
                    val isSelected = selectedApps.contains(app.packageName)
                    AppSelectRow(
                        appInfo = app,
                        isSelected = isSelected,
                        onSelectionChanged = {
                            viewModel.onAppSelectionChanged(app.packageName, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSelectRow(
    appInfo: AppInfo,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(!isSelected) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = appInfo.icon),
            contentDescription = appInfo.name,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(appInfo.name, modifier = Modifier.weight(1f))
        Checkbox(checked = isSelected, onCheckedChange = onSelectionChanged)
    }
}