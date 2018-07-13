package models

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.Date

case class SinglePersonTaskUpdateForm (
                      description : String,
                      startDate : ZonedDateTime,
                      endDate : ZonedDateTime,
                      category : String,
                      status : String,
                      selectSingleTask : String,
                      alertNumbers : List[Int],
                      alertSelects : List[String],
                      isActive : Boolean
                    )

object SinglePersonTaskUpdateForm {
  def newFrom(description : String,
              startDate : Date,
              endDate : Date,
              category : String,
              status : String,
              selectSingleTask : String,
              alertNumbers : List[Int],
              alertSelects : List[String],
              isActive : Boolean) = {
    SinglePersonTaskUpdateForm(
      description,
      ZonedDateTime.ofInstant(startDate.toInstant, ZoneId.of("UTC")),
      ZonedDateTime.ofInstant(endDate.toInstant, ZoneId.of("UTC")),
      category,
      status,
      selectSingleTask,
      alertNumbers,
      alertSelects,
      isActive
    )
  }

  def toTuple(tf: SinglePersonTaskUpdateForm) =
    Some((
      tf.description,
      Date.from(tf.startDate.toInstant),
      Date.from(tf.endDate.toInstant),
      tf.category,
      tf.status,
      tf.selectSingleTask,
      tf.alertNumbers,
      tf.alertSelects,
      tf.isActive
    ))
}