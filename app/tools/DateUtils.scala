package tools

import java.time.format.DateTimeFormatter

object DateUtils {
  val dateTimeFormatterUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
}
