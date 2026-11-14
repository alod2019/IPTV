package ru.alod.iptv

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import ru.alod.iptv.ui.main.MainScreen
import ru.alod.iptv.ui.player.PlayerScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider {
      entry<Main> {
        MainScreen(onChannelClick = { channelId -> backStack.add(Player(channelId)) })
      }
      entry<Player> { key ->
        PlayerScreen(channelId = key.channelId, onBack = { backStack.removeLastOrNull() })
      }
    },
  )
}
