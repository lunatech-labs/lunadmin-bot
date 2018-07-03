package models

import play.api.libs.json.Json

case class UserForm (
   mail : String,
   password : String,
   firstName : String,
   lastName : String
)

object UserForm {
  implicit val fmt = Json.format[UserForm]
}
