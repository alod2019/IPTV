package ru.alod.iptv

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class Player(val channelId: Long) : NavKey
