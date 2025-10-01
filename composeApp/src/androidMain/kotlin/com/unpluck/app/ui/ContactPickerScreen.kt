package com.unpluck.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unpluck.app.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(viewModel: MainViewModel) {
    val allContacts by viewModel.allContacts
    val selectedContactIds by viewModel.selectedContactIds

    Scaffold (
        topBar = {
            TopAppBar(
                title = { Text("Select Allowed Contacts") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton (onClick = { viewModel.saveContactSelection() }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn (modifier = Modifier.padding(padding).fillMaxSize()) {
            items(allContacts) { contact ->
                val isSelected = selectedContactIds.contains(contact.id)
                Row (
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.onContactSelectionChanged(contact.id, !isSelected)
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(contact.name, modifier = Modifier.weight(1f))
                    Checkbox(checked = isSelected, onCheckedChange = null)
                }
            }
        }
    }
}