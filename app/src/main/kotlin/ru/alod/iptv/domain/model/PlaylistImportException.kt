package ru.alod.iptv.domain.model

enum class ImportError {
  NETWORK,
  TIMEOUT,
  HTTP,
  INVALID_FORMAT,
  EMPTY,
  TOO_LARGE,
  FILE_ACCESS,
}

class PlaylistImportException(
  val error: ImportError,
  cause: Throwable? = null,
) : Exception(cause)
