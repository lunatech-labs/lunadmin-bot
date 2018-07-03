package models

import java.text.SimpleDateFormat
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import models.TaskType.TaskType
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import tools.DateUtils

import scala.util.{Failure, Success}




abstract class Task(_id: String = BSONObjectID.generate().stringify,
                    description: String,
                    startDate: ZonedDateTime,
                    endDate: ZonedDateTime,
                    status: String,
                    category: String,
                    alert: Option[List[ZonedDateTime]],
                    `type`: TaskType) {

  def getNameById(userList: List[User], groupList: List[UserGroup]): String

  private def getDate(date: ZonedDateTime): String = DateUtils.dateTimeFormatterUtc.format(date)

  def getAlert(): String = {
    var stringToReturn = ""
    if (alert.isDefined) {
      alert.get.foreach(d => stringToReturn = stringToReturn + getDate(d))
    } else {
      stringToReturn = stringToReturn + "No alert set up"
    }
    stringToReturn
  }

  def getId() = _id

  def getDescription() = description

  def getStartDate() = getDate(startDate)

  def getEndDate() = getDate(endDate)

  def getStatus() = status

  def getCategory() = category
}
