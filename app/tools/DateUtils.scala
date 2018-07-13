package tools

import java.time.format.DateTimeFormatter
import java.util.Date

import play.api.data.format.{Formats, Formatter}

object DateUtils {
  val dateTimeFormatterUtc = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val dateTimeFormatterLocal = DateTimeFormatter.ofPattern("dd-MM-yyyy - HH:mm:ss ")
  val dateTimeFormatterJavaUtil = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
  val dateFormatterJavaUtil = DateTimeFormatter.ofPattern("yyyy-mm-dd")
  val dateTimeUTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'")
  val dateTimeLocal: Formatter[Date] = Formats.dateFormat("yyyy-MM-dd'T'HH:mm")
  val dateLocal: Formatter[Date] = Formats.dateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

}
