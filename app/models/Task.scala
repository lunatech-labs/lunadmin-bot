package models

import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import models.TaskType.TaskType
import reactivemongo.bson.BSONObjectID
import tools.DateUtils

import scala.concurrent.duration._

abstract class Task(_id: String = BSONObjectID.generate().stringify,
                    description: String,
                    startDate: ZonedDateTime,
                    endDate: ZonedDateTime,
                    status: String,
                    category: String,
                    alert: List[(Long,String)],
                    `type`: TaskType,
                    isActive : Boolean) {

  def getNameById(userList: List[User], groupList: List[UserGroup]): String

  private def getDate(date: ZonedDateTime): String = DateUtils.dateTimeFormatterUtc.format(date)

  def getAlertHtmlForm(alertNumber : Long, alertSelect : String): (Long,String) ={
    alertSelect match {
      case "minute" => (Duration(alertNumber,"milli").toMinutes,alertSelect)
      case "hour" =>  (Duration(alertNumber,"milli").toHours,alertSelect)
      case "day" => (Duration(alertNumber,"milli").toDays,alertSelect)
    }
  }

  def getId() = _id

  def getDescription() = description

  def getStartDate() = getDate(startDate)

  def getDateInJavaDate(zonedDateTime: ZonedDateTime) = DateUtils.dateTimeFormatterJavaUtil.format(zonedDateTime)

  def getEndDate() = getDate(endDate)

  def getStatus() = status

  def getCategory() = category
}
