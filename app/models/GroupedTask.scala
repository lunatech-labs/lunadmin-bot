package models

import akka.http.scaladsl.model.headers.Date

class GroupedTask (
  id : Int,
  description : String,
  startDate : Date,
  endDate : Date,
  status : String,
  employeeId : Int,
  category : String,
  alert : List[Date]
) extends Task(id,description,startDate,endDate,status,category,alert){

}
