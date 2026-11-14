package ru.alod.iptv.ui.main

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import ru.alod.iptv.IptvApplication
import ru.alod.iptv.R
import ru.alod.iptv.domain.model.Channel
import ru.alod.iptv.domain.model.ImportError
import ru.alod.iptv.domain.model.Playlist
import ru.alod.iptv.domain.model.PlaylistSource
import ru.alod.iptv.ui.theme.IPTVTheme

@Composable
fun MainScreen(
  onChannelClick: (Long) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = run {
    val application = LocalContext.current.applicationContext as IptvApplication
    viewModel { MainScreenViewModel(application.container.repository) }
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (val current = state) {
    MainScreenUiState.Loading -> LoadingContent(modifier)
    is MainScreenUiState.Error -> ErrorContent(current.error, viewModel::retry, modifier)
    is MainScreenUiState.Content -> MainScreenContent(
      state = current,
      onPlaylistSelect = viewModel::selectPlaylist,
      onQueryChange = viewModel::setQuery,
      onGroupSelect = viewModel::selectGroup,
      onFavoritesToggle = viewModel::toggleFavorites,
      onImportUrl = viewModel::importUrl,
      onImportFile = viewModel::importFile,
      onEpgConfirmation = viewModel::resolveEpgConfirmation,
      onRefresh = viewModel::refresh,
      onDeletePlaylist = viewModel::deleteSelectedPlaylist,
      onFavoriteToggle = viewModel::setFavorite,
      onChannelClick = { channel ->
        viewModel.selectChannel(channel)
        onChannelClick(channel.id)
      },
      onDismissError = viewModel::dismissError,
      onDismissImportMessage = viewModel::dismissImportMessage,
      modifier = modifier,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenContent(
  state: MainScreenUiState.Content,
  onPlaylistSelect: (Long) -> Unit,
  onQueryChange: (String) -> Unit,
  onGroupSelect: (String?) -> Unit,
  onFavoritesToggle: () -> Unit,
  onImportUrl: (String, String) -> Unit,
  onImportFile: (String, String) -> Unit,
  onEpgConfirmation: (Boolean) -> Unit,
  onRefresh: () -> Unit,
  onDeletePlaylist: () -> Unit,
  onFavoriteToggle: (Channel) -> Unit,
  onChannelClick: (Channel) -> Unit,
  onDismissError: () -> Unit,
  onDismissImportMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showAddDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var pendingPlaylistName by rememberSaveable { mutableStateOf("") }
  val context = LocalContext.current
  val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      onImportFile(pendingPlaylistName, uri.toString())
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(state.selectedPlaylist?.name ?: stringResource(R.string.app_name)) },
        actions = {
          IconButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_playlist))
          }
          if (state.selectedPlaylist != null) {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
              Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
            IconButton(onClick = { showDeleteDialog = true }) {
              Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_playlist))
            }
          }
        },
      )
    },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
      if (state.isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      if (state.playlists.size > 1) {
        LazyRow(
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.playlists, key = Playlist::id) { playlist ->
            FilterChip(
              selected = playlist.id == state.selectedPlaylistId,
              onClick = { onPlaylistSelect(playlist.id) },
              label = { Text(playlist.name) },
            )
          }
        }
      }
      if (state.playlists.isNotEmpty()) {
        OutlinedTextField(
          value = state.query,
          onValueChange = onQueryChange,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          singleLine = true,
          label = { Text(stringResource(R.string.search_channels)) },
        )
        LazyRow(
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item {
            FilterChip(
              selected = state.selectedGroup == null && !state.favoritesOnly,
              onClick = {
                onGroupSelect(null)
                if (state.favoritesOnly) onFavoritesToggle()
              },
              label = { Text(stringResource(R.string.all_channels)) },
            )
          }
          item {
            FilterChip(
              selected = state.favoritesOnly,
              onClick = onFavoritesToggle,
              label = { Text(stringResource(R.string.favorites)) },
            )
          }
          items(state.groups) { group ->
            FilterChip(
              selected = state.selectedGroup == group && !state.favoritesOnly,
              onClick = {
                onGroupSelect(group)
                if (state.favoritesOnly) onFavoritesToggle()
              },
              label = { Text(group) },
            )
          }
        }
      }
      if (state.skippedChannels != null || state.epgError) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
              state.skippedChannels?.let { skipped ->
                Text(stringResource(R.string.skipped_channels, skipped))
              }
              if (state.epgError) Text(stringResource(R.string.epg_import_failed))
            }
            IconButton(onClick = onDismissImportMessage) {
              Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
          }
        }
      }
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
          state.playlists.isEmpty() -> EmptyContent(
            title = stringResource(R.string.no_playlists_title),
            message = stringResource(R.string.no_playlists_message),
            action = stringResource(R.string.add_playlist),
            onAction = { showAddDialog = true },
          )
          state.visibleChannels.isEmpty() -> EmptyContent(
            title = stringResource(R.string.no_channels_title),
            message = stringResource(R.string.no_channels_message),
          )
          else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.visibleChannels, key = Channel::id) { channel ->
              ChannelRow(
                channel = channel,
                onClick = { onChannelClick(channel) },
                onFavoriteToggle = { onFavoriteToggle(channel) },
              )
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    AddPlaylistDialog(
      isLoading = state.isRefreshing,
      onDismiss = { showAddDialog = false },
      onImportUrl = { name, url ->
        onImportUrl(name, url)
        showAddDialog = false
      },
      onChooseFile = { name ->
        pendingPlaylistName = name
        fileLauncher.launch(arrayOf("*/*"))
        showAddDialog = false
      },
    )
  }
  if (state.isEpgConfirmationVisible) {
    AlertDialog(
      onDismissRequest = { onEpgConfirmation(false) },
      title = { Text(stringResource(R.string.epg_confirmation_title)) },
      text = { Text(stringResource(R.string.epg_confirmation_message)) },
      confirmButton = {
        TextButton(onClick = { onEpgConfirmation(true) }) {
          Text(stringResource(R.string.use_epg))
        }
      },
      dismissButton = {
        TextButton(onClick = { onEpgConfirmation(false) }) {
          Text(stringResource(R.string.skip_epg))
        }
      },
    )
  }
  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(R.string.delete_playlist)) },
      text = { Text(stringResource(R.string.delete_playlist_confirmation, state.selectedPlaylist?.name.orEmpty())) },
      confirmButton = {
        TextButton(onClick = {
          onDeletePlaylist()
          showDeleteDialog = false
        }) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
      },
    )
  }
  state.operationError?.let { error ->
    AlertDialog(
      onDismissRequest = onDismissError,
      title = { Text(stringResource(R.string.app_name)) },
      text = { Text(importErrorText(error)) },
      confirmButton = {
        TextButton(onClick = onDismissError) { Text(stringResource(R.string.close)) }
      },
    )
  }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supportingContent = {
      Column {
        channel.groupName?.let { Text(it) }
        channel.currentProgram?.let {
          Text(stringResource(R.string.now_program, it.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        channel.nextProgram?.let {
          Text(stringResource(R.string.next_program, it.title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    },
    leadingContent = {
      if (channel.logoUrl == null) {
        ChannelLogoPlaceholder(channel.name)
      } else {
        SubcomposeAsyncImage(
          model = channel.logoUrl,
          contentDescription = stringResource(R.string.channel_logo, channel.name),
          modifier = Modifier.size(56.dp),
          contentScale = ContentScale.Fit,
          loading = { ChannelLogoPlaceholder(channel.name) },
          error = { ChannelLogoPlaceholder(channel.name) },
        )
      }
    },
    trailingContent = {
      IconButton(onClick = onFavoriteToggle) {
        Icon(
          imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
          contentDescription = stringResource(
            if (channel.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites,
            channel.name,
          ),
          tint = if (channel.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

@Composable
private fun ChannelLogoPlaceholder(name: String) {
  Surface(
    modifier = Modifier.size(56.dp),
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.primaryContainer,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(name.firstOrNull()?.uppercase() ?: "TV", style = MaterialTheme.typography.titleLarge)
    }
  }
}

@Composable
private fun AddPlaylistDialog(
  isLoading: Boolean,
  onDismiss: () -> Unit,
  onImportUrl: (String, String) -> Unit,
  onChooseFile: (String) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var url by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_playlist)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          singleLine = true,
          label = { Text(stringResource(R.string.playlist_name)) },
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          singleLine = true,
          label = { Text(stringResource(R.string.playlist_url)) },
        )
        TextButton(onClick = { onChooseFile(name) }, enabled = !isLoading) {
          Text(stringResource(R.string.choose_file))
        }
      }
    },
    confirmButton = {
      Button(onClick = { onImportUrl(name, url) }, enabled = url.isNotBlank() && !isLoading) {
        Text(stringResource(R.string.import_url))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun EmptyContent(title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
  Column(
    modifier = Modifier.padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(message, style = MaterialTheme.typography.bodyLarge)
    if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
  }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
private fun ErrorContent(error: ImportError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(importErrorText(error), modifier = Modifier.padding(32.dp))
      Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
  }
}

@Composable
internal fun importErrorText(error: ImportError): String = stringResource(
  when (error) {
    ImportError.NETWORK -> R.string.error_network
    ImportError.TIMEOUT -> R.string.error_timeout
    ImportError.HTTP -> R.string.error_http
    ImportError.INVALID_FORMAT -> R.string.error_invalid_format
    ImportError.EMPTY -> R.string.error_empty
    ImportError.TOO_LARGE -> R.string.error_too_large
    ImportError.FILE_ACCESS -> R.string.error_file_access
  },
)

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
  IPTVTheme {
    MainScreenContent(
      state = MainScreenUiState.Content(
        playlists = listOf(Playlist(1, "Мой плейлист", PlaylistSource.URL, "", null, 0, null)),
        selectedPlaylistId = 1,
        channels = listOf(Channel(1, 1, "one", "Новости", "https://example.com/live.m3u8", null, "Общие", null, true)),
      ),
      onPlaylistSelect = {},
      onQueryChange = {},
      onGroupSelect = {},
      onFavoritesToggle = {},
      onImportUrl = { _, _ -> },
      onImportFile = { _, _ -> },
      onEpgConfirmation = {},
      onRefresh = {},
      onDeletePlaylist = {},
      onFavoriteToggle = {},
      onChannelClick = {},
      onDismissError = {},
      onDismissImportMessage = {},
    )
  }
}
