package models

import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

case class TaskForm (
                      description : String,
                      startDate : ZonedDateTime,
                      endDate : ZonedDateTime,
                      category : String,
                      status : String,
                      taskChoice : String,
                      selectSingleTask : String,
                      selectGroupedTask : List[String],
                      alertNumbers : List[Int],
                      alertSelects : List[String]
                    )

object TaskForm {
  def newFrom(description : String,
              startDate : Date,
              endDate : Date,
              category : String,
              status : String,
              taskChoice : String,
              selectSingleTask : String,
              selectGroupedTask : List[String],
              alertNumbers : List[Int],
              alertSelects : List[String]) = {
    TaskForm(
      description,
        ZonedDateTime.ofInstant(startDate.toInstant, ZoneId.of("UTC")),
        ZonedDateTime.ofInstant(endDate.toInstant, ZoneId.of("UTC")),
        category,
        status,
        taskChoice,
        selectSingleTask,
        selectGroupedTask,
        alertNumbers,
        alertSelects
    )
  }

  def toTuple(tf: TaskForm) =
    Some((
      tf.description,
      Date.from(tf.startDate.toInstant),
      Date.from(tf.endDate.toInstant),
        tf.category,
        tf.status,
        tf.taskChoice,
        tf.selectSingleTask,
        tf.selectGroupedTask,
        tf.alertNumbers,
        tf.alertSelects
    ))
}
