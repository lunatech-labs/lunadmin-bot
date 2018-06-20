package models

import akka.http.scaladsl.model.headers.Date

case class User(
  mail : String,
  password : String,
  firstName : String,
  lastName : String,
  id : Option[Int] = None,
  birthDate : Option[Date] = None,
  groupId : Option[List[Int]] = None,
  status : Option[String] = None,
  hireDate : Option[Date] = None,
  picture : Option[String] = None, // pathToFile
  phone : Option[String] = None, // pour le +33 au cas ou
  cloudLink : Option[List[String]] = None
)