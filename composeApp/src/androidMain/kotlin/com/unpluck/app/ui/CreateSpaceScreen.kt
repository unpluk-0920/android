// In ui/CreateSpaceScreen.kt
package com.unpluck.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unpluck.app.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSpaceScreen(viewModel: MainViewModel) {
    var spaceName by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Spa,
            contentDescription = "Create Space",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Create Your First Space",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "A 'Space' is a collection of apps you want to allow in Focus Mode. Let's start with one for work or study.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = spaceName,
            onValueChange = { spaceName = it },
            label = { Text("e.g., Work, Study, Reading") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.saveSpace(context, spaceName) },
            enabled = spaceName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Space & Continue")
        }
    }
}