package models

import akka.http.scaladsl.model.headers.Date

class SinglePersonTask (
   id : Int,
   description : String,
   startDate : Date,
   endDate : Date,
   status : String,
   employeeId : Int,
   category : String,
   alert : List[Date]
){

}
