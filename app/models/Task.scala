package models

import akka.http.scaladsl.model.headers.Date

abstract class Task(
  id : Int,
  description : String,
  startDate : Date,
  endDate : Date,
  status : String,
  category : String,
  alert : List[Date]
){


}

