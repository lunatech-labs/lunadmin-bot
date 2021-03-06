package models

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import models.TaskType.TaskType
import tools.DateUtils
import scala.concurrent.duration._

trait Task {
  val _id: String
  val description: String
  val startDate: ZonedDateTime
  val endDate: ZonedDateTime
  val status: String
  val category: String
  val alert: List[(Long,String)]
  val `type`: TaskType
  val isActive : Boolean

  def getNameById(userList: List[User], groupList: List[UserGroup]): String

  private def getDate(date: ZonedDateTime): String = DateUtils.dateTimeFormatterUtc.format(date)

  def getAlertHtmlForm(alertNumber : Long, alertSelect : String): (Long,String) ={
    val duration = Duration(alertNumber, TimeUnit.MILLISECONDS)
    alertSelect match {
      case "minute" => (duration.toMinutes, alertSelect)
      case "hour" =>  (duration.toHours, alertSelect)
      case "day" => (duration.toDays, alertSelect)
      case "week" => (duration.toDays/7,alertSelect)
      case "month" => (duration.toDays/30,alertSelect)
      case "year" => (duration.toDays/365,alertSelect)
    }
  }

  def getDateInJavaDate(zonedDateTime: ZonedDateTime) = DateUtils.dateTimeFormatterJavaUtil.format(zonedDateTime)

}
