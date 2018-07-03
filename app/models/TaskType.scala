package models

import play.api.libs.json.Json

object TaskType extends Enumeration {
   type TaskType = Value
   val SINGLE, GROUPED = Value
}