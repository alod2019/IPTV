package ru.alod.iptv.ui.player

import androidx.activity.compose.LocalActivity
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ru.alod.iptv.IptvApplication
import ru.alod.iptv.R

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
  channelId: Long,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PlayerViewModel = run {
    val application = LocalContext.current.applicationContext as IptvApplication
    viewModel { PlayerViewModel(application, application.container.repository, channelId) }
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current
  val activity = LocalActivity.current
  var isFullscreen by rememberSaveable { mutableStateOf(false) }
  var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> viewModel.resume()
        Lifecycle.Event.ON_STOP -> viewModel.pause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.pause()
    }
  }

  DisposableEffect(activity, isFullscreen) {
    val window = activity?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    if (isFullscreen) {
      controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      controller?.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller?.show(WindowInsetsCompat.Type.systemBars())
    }
    onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
  }

  Scaffold(
    modifier = modifier.background(Color.Black),
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(state.channel?.name ?: stringResource(R.string.app_name)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
        actions = {
          IconButton(onClick = {
            resizeMode = when (resizeMode) {
              AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
              AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
              else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
          }) {
            Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.aspect_ratio))
          }
          IconButton(onClick = { isFullscreen = !isFullscreen }) {
            Icon(
              if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
              contentDescription = stringResource(if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen),
            )
          }
        },
      )
    },
  ) { padding ->
    Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          PlayerView(context).apply {
            keepScreenOn = true
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
          }
        },
        update = { view ->
          view.player = state.player
          view.resizeMode = resizeMode
        },
      )
      if (state.isLoading || state.isBuffering) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      }
      state.error?.let { error ->
        Column(
          modifier = Modifier.align(Alignment.Center).padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(playerErrorText(error), color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
          if (error != PlayerError.NOT_FOUND) {
            Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
          }
        }
      }
    }
  }
}

@Composable
private fun playerErrorText(error: PlayerError): String = stringResource(
  when (error) {
    PlayerError.NETWORK -> R.string.player_error_network
    PlayerError.TIMEOUT -> R.string.player_error_timeout
    PlayerError.UNSUPPORTED -> R.string.player_error_unsupported
    PlayerError.NOT_FOUND -> R.string.player_channel_not_found
    PlayerError.GENERIC -> R.string.player_error_generic
  },
)
