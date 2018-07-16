package models

import play.api.libs.json._

case class LoginForm (
     mail : String,
     password : String
)

object LoginForm{
  implicit val fmt = Json.format[LoginForm]
}
