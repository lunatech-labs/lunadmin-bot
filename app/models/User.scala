package models

import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}
import java.util.Date

import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import tools.DateUtils

case class User(
                 mail : String,
                 password : String,
                 firstName : String,
                 lastName : String,
                 _id : String = BSONObjectID.generate().stringify,
                 birthDate : LocalDate,
                 groupName : Option[List[String]] = None,
                 status : Option[String] = Some("user"),
                 hireDate : LocalDate,
                 picture : Option[String] = None, // link
                 phone : Option[String] = None, // pour le +33 au cas ou
                 cloudLinks : Option[List[(String,String)]] = None,
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
  implicit val dateFormatter = new Format[LocalDate] {
    def reads(jsValue: JsValue): JsResult[LocalDate] = {
      (jsValue \ "$date").validate[Long].map{ l =>
        val zdt = new Date(l).toInstant.atZone(ZoneId.of("UTC"))
        LocalDate.of(zdt.getYear, zdt.getMonthValue, zdt.getDayOfMonth)
      }
    }

    def writes(date: LocalDate): JsValue = {
        Json.obj("$date" -> date.atStartOfDay(ZoneId.of("UTC")).toInstant.toEpochMilli)
    }
  }

  implicit val fmt = Json.format[User]
}