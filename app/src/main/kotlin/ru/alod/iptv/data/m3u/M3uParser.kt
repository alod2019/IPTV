package ru.alod.iptv.data.m3u

import java.io.Reader
import java.net.URI

class M3uParser {
  fun parse(reader: Reader): ParsedPlaylist {
    val channels = mutableListOf<ParsedChannel>()
    var pending: PendingChannel? = null
    var skippedChannels = 0
    var firstLine = true
    var epgUrl: String? = null

    reader.buffered().forEachLine { rawLine ->
      val line = if (firstLine) rawLine.removePrefix("\uFEFF").trim() else rawLine.trim()
      firstLine = false
      when {
        line.isEmpty() -> Unit
        line.startsWith("#EXTM3U", ignoreCase = true) -> {
          val attributes = parseAttributes(line.substringAfter(' ', ""))
          epgUrl = listOf("url-tvg", "x-tvg-url", "tvg-url")
            .firstNotNullOfOrNull { key -> attributes[key]?.toNullable() }
        }
        line.startsWith("#EXTINF:", ignoreCase = true) -> {
          if (pending != null) skippedChannels++
          val metadata = line.substringAfter(':')
          pending = PendingChannel(
            name = findTitle(metadata),
            attributes = parseAttributes(metadata),
          )
        }
        line.startsWith("#EXTGRP:", ignoreCase = true) -> {
          val group = line.substringAfter(':').trim()
          val current = pending ?: PendingChannel()
          pending = current.copy(attributes = current.attributes + ("group-title" to group))
        }
        line.startsWith("#") -> Unit
        else -> {
          val current = pending
          if (!isHttpUrl(line)) {
            if (current != null) skippedChannels++
          } else {
            val name = current?.name?.takeIf(String::isNotBlank)
              ?: current?.attributes?.get("tvg-name")?.takeIf(String::isNotBlank)
              ?: fallbackName(line)
            channels += ParsedChannel(
              name = name,
              streamUrl = line,
              logoUrl = current?.attributes?.get("tvg-logo")?.toNullable(),
              groupName = current?.attributes?.get("group-title")?.toNullable(),
              tvgId = current?.attributes?.get("tvg-id")?.toNullable(),
            )
          }
          pending = null
        }
      }
    }
    if (pending != null) skippedChannels++
    return ParsedPlaylist(channels = channels, skippedChannels = skippedChannels, epgUrl = epgUrl)
  }

  private fun parseAttributes(metadata: String): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    var index = 0
    while (index < metadata.length) {
      while (index < metadata.length && metadata[index].isWhitespace()) index++
      val keyStart = index
      while (index < metadata.length && metadata[index] != '=' && !metadata[index].isWhitespace() && metadata[index] != ',') index++
      if (index >= metadata.length || metadata[index] != '=') {
        if (index < metadata.length && metadata[index] == ',') index++
        continue
      }
      val key = metadata.substring(keyStart, index).lowercase()
      index++
      val value = if (index < metadata.length && metadata[index] == '"') {
        index++
        val valueStart = index
        while (index < metadata.length && metadata[index] != '"') index++
        val parsed = metadata.substring(valueStart, index)
        if (index < metadata.length) index++
        parsed
      } else {
        val valueStart = index
        while (index < metadata.length && !metadata[index].isWhitespace() && metadata[index] != ',') index++
        metadata.substring(valueStart, index)
      }
      if (key.isNotEmpty()) attributes[key] = value
      if (index < metadata.length && metadata[index] == ',') index++
    }
    return attributes
  }

  private fun findTitle(metadata: String): String {
    var quoted = false
    metadata.forEachIndexed { index, character ->
      when (character) {
        '"' -> quoted = !quoted
        ',' -> if (!quoted) return metadata.substring(index + 1).trim()
      }
    }
    return ""
  }

  private fun isHttpUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
      !uri.host.isNullOrBlank()
  }.getOrDefault(false)

  private fun fallbackName(url: String): String =
    url.substringAfterLast('/').substringBefore('?').ifBlank { url }

  private fun String.toNullable(): String? = trim().ifEmpty { null }

  private data class PendingChannel(
    val name: String = "",
    val attributes: Map<String, String> = emptyMap(),
  )
}

data class ParsedPlaylist(
  val channels: List<ParsedChannel>,
  val skippedChannels: Int,
  val epgUrl: String? = null,
)

data class ParsedChannel(
  val name: String,
  val streamUrl: String,
  val logoUrl: String?,
  val groupName: String?,
  val tvgId: String?,
)
