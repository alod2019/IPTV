package ru.alod.iptv.ui.player

import android.view.View
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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
  var areControlsVisible by remember { mutableStateOf(false) }
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

  DisposableEffect(activity) {
    val window = activity?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
  }

  LaunchedEffect(activity, areControlsVisible) {
    val window = activity?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    if (areControlsVisible) {
      controller?.show(WindowInsetsCompat.Type.systemBars())
    } else {
      controller?.hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        PlayerView(context).apply {
          keepScreenOn = true
          useController = true
          controllerAutoShow = false
          setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
          setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
              areControlsVisible = visibility == View.VISIBLE
            },
          )
        }
      },
      update = { view ->
        view.player = state.player
        view.resizeMode = resizeMode
      },
    )
    if (areControlsVisible) {
      CenterAlignedTopAppBar(
        modifier = Modifier.align(Alignment.TopCenter),
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
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.Black.copy(alpha = 0.65f),
        ),
      )
    }
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
