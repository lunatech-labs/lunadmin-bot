package models

import java.util.Date
import java.time.{ZoneId, ZonedDateTime}
import models.TaskType.TaskType
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat

case class GroupedTask (
                         _id : String = BSONObjectID.generate().stringify,
                         description : String,
                         startDate : ZonedDateTime,
                         endDate : ZonedDateTime,
                         status : String,
                         groupName : List[String],
                         category : String,
                         alert : List[(Long,String)],
                         `type`: TaskType = TaskType.GROUPED,
                         isActive : Boolean = true
) extends Task{

  override def getNameById(userList : List[User], groupList : List[UserGroup]): String = {

    val listOfGroupIn = groupList.filter(userG => groupName.contains(userG.name))
    var listOfPeopleIn = List[User]()
    userList.foreach(p => if(p.groupName.isDefined){
      val listOfIntersection = p.groupName.get.intersect(listOfGroupIn.map(userG => userG._id))
      if(listOfIntersection.nonEmpty)
          listOfPeopleIn = listOfPeopleIn :+ p
    })


    if(listOfPeopleIn.nonEmpty) {
      var stringToReturn = "Group - "
    for(g <- listOfGroupIn){
        stringToReturn = stringToReturn + s"${g.name},"
    }
      stringToReturn = stringToReturn.dropRight(1)
      stringToReturn
    }else{
      "Nobody is affected to this task"
    }
  }
}

object GroupedTask{
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

  implicit val fmt = Json.format[GroupedTask]
}


