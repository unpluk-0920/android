import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unpluck.app.MainViewModel
import com.unpluck.app.defs.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceListScreen(viewModel: MainViewModel) {
    val spaces by viewModel.allSpaces.collectAsState()

    Scaffold (
        topBar = {
            TopAppBar(title = { Text("Your Spaces") }, navigationIcon = {
                IconButton (onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton (onClick = { viewModel.navigateToCreateSpace() }) {
                Icon(Icons.Default.Add, "Add Space")
            }
        }
    ) { padding ->
        LazyColumn (modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            items(spaces) { space ->
                SpaceListItem(
                    space = space,
                    onSpaceClicked = { viewModel.setActiveSpace(space) },
                    onSettingsClicked = { viewModel.navigateToSettings(space) }
                )
            }
        }
    }
}

@Composable
fun SpaceListItem(space: Space, onSpaceClicked: () -> Unit, onSettingsClicked: () -> Unit) {
    Card (modifier = Modifier.fillMaxWidth().clickable { onSpaceClicked() }) {
        Row (Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(space.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onSettingsClicked) {
                Icon(Icons.Default.Settings, "Settings")
            }
        }
    }
}