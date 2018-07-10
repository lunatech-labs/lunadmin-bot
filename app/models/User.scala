package models

import java.time.ZonedDateTime
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID

case class User(
                 mail : String,
                 password : String,
                 firstName : String,
                 lastName : String,
                 _id : String = BSONObjectID.generate().stringify,
                 birthDate : Option[ZonedDateTime] = None,
                 groupName : Option[List[String]] = None,
                 status : Option[String] = None,
                 hireDate : Option[ZonedDateTime] = None,
                 picture : Option[String] = None, // link
                 phone : Option[String] = None, // pour le +33 au cas ou
                 cloudLinks : Option[List[(String,String)]] = None
)

object User {
  implicit val fmt = Json.format[User]
}