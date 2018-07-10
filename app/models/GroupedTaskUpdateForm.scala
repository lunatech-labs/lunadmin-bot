package models

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.Date

case class GroupedTaskUpdateForm (
                                        description : String,
                                        startDate : ZonedDateTime,
                                        endDate : ZonedDateTime,
                                        category : String,
                                        status : String,
                                        selectGroupedTask : List[String],
                                        alertNumbers : List[Int],
                                        alertSelects : List[String]
                                      )

object GroupedTaskUpdateForm {
  def newFrom(description : String,
              startDate : Date,
              endDate : Date,
              category : String,
              status : String,
              selectGroupedTask : List[String],
              alertNumbers : List[Int],
              alertSelects : List[String]) = {
    GroupedTaskUpdateForm(
      description,
      ZonedDateTime.ofInstant(startDate.toInstant, ZoneId.of("UTC")),
      ZonedDateTime.ofInstant(endDate.toInstant, ZoneId.of("UTC")),
      category,
      status,
      selectGroupedTask,
      alertNumbers,
      alertSelects
    )
  }

  def toTuple(tf: GroupedTaskUpdateForm) =
    Some((
      tf.description,
      Date.from(tf.startDate.toInstant),
      Date.from(tf.endDate.toInstant),
      tf.category,
      tf.status,
      tf.selectGroupedTask,
      tf.alertNumbers,
      tf.alertSelects
    ))
}