package models

import java.text.SimpleDateFormat
import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import models.TaskType.TaskType
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import tools.DateUtils

import scala.concurrent.duration.Duration


case class SinglePersonTask (
                              _id : String = BSONObjectID.generate().stringify,
                              description : String,
                              startDate : ZonedDateTime,
                              endDate : ZonedDateTime,
                              status : String,
                              employeeId : String,
                              category : String,
                              alert : List[(Long,String)],
                              `type`: TaskType = TaskType.SINGLE,
                              isActive : Boolean = true
)extends Task{

  override def getNameById(userList : List[User], groupList : List[UserGroup]): String = {
    val employeeConcerned = userList.find(p => p._id == employeeId)
    var stringToReturn = ""
    if(employeeConcerned.isDefined) {
      stringToReturn = s"${employeeConcerned.get.firstName} ${employeeConcerned.get.lastName}"
    }else{
      stringToReturn = "Error On Creation Of Task"
    }
    stringToReturn
  }
}
object SinglePersonTask {
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
  implicit val fmt = Json.format[SinglePersonTask]
}

