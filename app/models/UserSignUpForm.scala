package models

import play.api.libs.json.Json

case class UserSignUpForm (
   mail : String,
   password : String,
   firstName : String,
   lastName : String

)

object UserSignUpForm {
  implicit val fmt = Json.format[UserSignUpForm]
}
