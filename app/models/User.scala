package models

import akka.http.scaladsl.model.headers.Date

class User(
  id : Int,
  password : String,
  lastName : String,
  firstName : String,
  mail : String,
  birthDate : Option[Date],
  groupId : Option[List[Int]],
  status : String,
  hireDate : Date,
  picture : Option[String], // pathToFile
  phone : String, // pour le +33 au cas ou
  cloudLink : Option[List[String]]
){





}