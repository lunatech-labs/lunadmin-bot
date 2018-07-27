package models

import java.time.{ZoneId, ZonedDateTime}
import java.util.Date
import models.TaskType.TaskType
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

case class TaskDescription (
                           _id : String = BSONObjectID.generate().stringify,
                           var description : String,
                           startDate : ZonedDateTime,
                           var category : String,
                           status : String,
                           `type`: TaskType
                           ){
  def getType() : String = `type`.toString
}

object TaskDescription {
  implicit val myEnumFormat = new Format[TaskType.TaskType] {
    def reads(json: JsValue) = JsSuccess(TaskType.withName(json.as[String]))
    def writes(myEnum: TaskType.TaskType) = JsString(myEnum.toString)
  }

  implicit val dateFormatter = new Format[ZonedDateTime] {
    def reads(jsValue: JsValue): JsResult[ZonedDateTime] = {
      (jsValue \ "$date").validate[Long].map { l => new Date(l).toInstant.atZone(ZoneId.of("UTC")) }
    }
    def writes(zdt: ZonedDateTime): JsValue = Json.obj("$date" -> zdt.toInstant.toEpochMilli)
  }
  implicit val fmt = Json.format[TaskDescription]
}
