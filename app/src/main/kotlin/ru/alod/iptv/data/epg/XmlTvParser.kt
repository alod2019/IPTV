package ru.alod.iptv.data.epg

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class XmlTvParser {
  fun parse(input: InputStream): List<ParsedEpgProgram> {
    val programs = mutableListOf<ParsedEpgProgram>()
    val factory = SAXParserFactory.newInstance().apply {
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }
    factory.newSAXParser().parse(input, EpgHandler(programs))
    return programs
  }

  private class EpgHandler(
    private val programs: MutableList<ParsedEpgProgram>,
  ) : DefaultHandler() {
    private var channelId: String? = null
    private var start: Long? = null
    private var end: Long? = null
    private var title: String? = null
    private var description: String? = null
    private var currentText: StringBuilder? = null

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
      when (qName) {
        "programme" -> {
          channelId = attributes.getValue("channel")
          start = parseDate(attributes.getValue("start"))
          end = parseDate(attributes.getValue("stop"))
          title = null
          description = null
        }
        "title" -> if (channelId != null && title == null) currentText = StringBuilder()
        "desc" -> if (channelId != null && description == null) currentText = StringBuilder()
      }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
      currentText?.append(ch, start, length)
    }

    override fun endElement(uri: String, localName: String, qName: String) {
      when (qName) {
        "title" -> {
          title = currentText.toString().trim().ifEmpty { null }
          currentText = null
        }
        "desc" -> {
          description = currentText.toString().trim().ifEmpty { null }
          currentText = null
        }
        "programme" -> {
          val currentChannel = channelId
          val currentStart = start
          val currentEnd = end
          val currentTitle = title
          if (!currentChannel.isNullOrBlank() && currentStart != null && currentEnd != null &&
            currentEnd > currentStart && currentTitle != null
          ) {
            programs += ParsedEpgProgram(
              channelId = currentChannel,
              title = currentTitle,
              description = description,
              startMillis = currentStart,
              endMillis = currentEnd,
            )
          }
          channelId = null
          currentText = null
        }
      }
    }

    private fun parseDate(value: String?): Long? {
      if (value.isNullOrBlank()) return null
      val patterns = if (value.trim().length == 14) {
        listOf("yyyyMMddHHmmss")
      } else {
        listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmm Z")
      }
      return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
          SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            if (!pattern.endsWith("Z")) timeZone = TimeZone.getTimeZone("UTC")
          }.parse(value.trim())?.time
        }.getOrNull()
      }
    }
  }
}

data class ParsedEpgProgram(
  val channelId: String,
  val title: String,
  val description: String?,
  val startMillis: Long,
  val endMillis: Long,
)
