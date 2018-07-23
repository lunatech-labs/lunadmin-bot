package models

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import java.util.Date
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import tools.DateUtils


case class User(
                 mail : String,
                 password : String,
                 firstName : String,
                 lastName : String,
                 _id : String = BSONObjectID.generate().stringify,
                 birthDate : Option[LocalDate] = None,
                 groupName : Option[List[String]] = None,
                 status : Option[String] = Some("user"),
                 hireDate : Option[LocalDate] = None,
                 picture : Option[String] = None, // link
                 phone : Option[String] = None, // pour le +33 au cas ou
                 cloudPaths : Option[List[(String,String)]] = None,
                 isActive : Boolean = true,
                 timeZone : String = "Europe/Paris"
){
  def getLocalDateInJavaDate(zonedDateTime: Option[ZonedDateTime], localTimeZoneId : String) = {
    if(zonedDateTime.isDefined) {
      DateUtils.dateFormatterJavaUtil.format(zonedDateTime.get.withZoneSameInstant(ZoneId.of(localTimeZoneId)))
    }
  }
}

object User {
  private val UTC: ZoneId = ZoneId.of("UTC")

  implicit val dateFormatter: Format[LocalDate] = new Format[LocalDate] {
    def reads(jsValue: JsValue): JsResult[LocalDate] = {
      (jsValue \ "$date").validateOpt[Long].map{ l =>
        val zdt = new Date(l.get).toInstant.atZone(UTC)
        LocalDate.of(zdt.getYear, zdt.getMonthValue, zdt.getDayOfMonth)
      }
    }

    def writes(date: LocalDate): JsValue = {
      Json.obj("$date" -> date.atStartOfDay(UTC).toInstant.toEpochMilli)
    }
  }

  implicit val fmt = Json.format[User]
}