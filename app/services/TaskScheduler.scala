package services

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.{ActorSystem, Cancellable}
import com.lunatech.slack.client.models.{AttachmentField, ChatMessage, User}
import controllers.Starter
import javax.inject.{Inject, Singleton}
import models.{GroupedTask, SinglePersonTask}
import play.api.Configuration
import tools.DateUtils

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

@Singleton
class TaskScheduler @Inject()(s: Starter, conf: Configuration)(implicit actorSystem: ActorSystem, ec: ExecutionContext) {

  var listOfScheduledTask: List[(String, Cancellable)] = List()

  val reminderTitle: String = "Reminder"
  val missionTitle: String = "Your Mission"
  val communColor: String = "85DF2D"
  val importantColor: String = "BB2100"
  val recurrentColor: String = "FFA316"
  val reminderColor: String = "00BABD"

  def setSlackBotMessageForSingleTask(task: SinglePersonTask): Unit = {
    s.userDataStore.findUserById(task.employeeId).foreach { optUser =>
      optUser.foreach { user =>
        if (Seq("Unique - Commune", "Unique - Importante").contains(task.status)) {
          scheduleASlackBotMessageOnce(task._id, user.mail, task.startDate, task.status, task.description)
          for (alert <- task.alert) {
            scheduleASlackBotAlertMessageOnce(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        } else {
          scheduleASlackBotMessageRecurrent(task._id, user.mail, task.startDate, task.status, task.description)
          for (alert <- task.alert) {
            scheduleASlackBotAlertMessageRecurrent(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        }
      }
    }
  }

  def setSlackBotMessageForGroupedTask(task: GroupedTask): Unit = {
    s.userDataStore.findUserDescriptionByListOfUserGroup(task.groupName).map(listOfUser =>
      for (user <- listOfUser) {
        if (Seq("Unique - Commune", "Unique - Importante").contains(task.status)) {
          scheduleASlackBotMessageOnce(task._id, user.mail, task.startDate, task.status, task.description)
          for (alert <- task.alert) {
            scheduleASlackBotAlertMessageOnce(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        } else {
          scheduleASlackBotMessageRecurrent(task._id, user.mail, task.startDate, task.status, task.description)
          for (alert <- task.alert) {
            scheduleASlackBotAlertMessageRecurrent(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        }
      }
    )
  }

  private def scheduleASlackBotMessageOnce(taskId: String, mail: String, startDate: ZonedDateTime, taskStatus: String, taskDescription: String): Unit = {
    val timeLeftUntilStartDate: Long = startDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli

    taskStatus match {
      case "Unique - Commune" =>
        scheduleAMessageOnceAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, missionTitle, communColor)

      case "Unique - Importante" =>
        scheduleAMessageOnceAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, missionTitle, importantColor)
    }
  }

  private def scheduleASlackBotAlertMessageOnce(taskId: String, mail: String, startDate: ZonedDateTime, taskStatus: String, taskDescription: String, alertDuration: Long): Unit = {
    val alertStartDate = getAlertStartDateFromStartDate(startDate, alertDuration)

    val timeLeftUntilStartDate: Long = alertStartDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli

    scheduleAMessageOnceAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, reminderTitle, reminderColor)
  }

  private def scheduleASlackBotMessageRecurrent(taskId: String, mail: String, startDate: ZonedDateTime, taskStatus: String, taskDescription: String): Unit = {
    var timeLeftUntilStartDate: Long = 0
    if (ZonedDateTime.now().toInstant.toEpochMilli <= startDate.toInstant.toEpochMilli) {
      timeLeftUntilStartDate = timeLeftUntilStartDate + startDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
    } else {
      timeLeftUntilStartDate = timeLeftUntilStartDate + defineAlertTimeLeftUntilStartDate(taskStatus, startDate)
    }

    scheduleARecurrentMessageAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, missionTitle, recurrentColor)
  }

  private def scheduleASlackBotAlertMessageRecurrent(taskId: String, mail: String, startDate: ZonedDateTime, taskStatus: String, taskDescription: String, alertDuration: Long): Unit = {
    var timeLeftUntilStartDate: Long = 0
    val alertStartDate = getAlertStartDateFromStartDate(startDate, alertDuration)

    if (ZonedDateTime.now().getDayOfYear <= alertStartDate.getDayOfYear) {
      timeLeftUntilStartDate = timeLeftUntilStartDate + alertStartDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
    } else {
      timeLeftUntilStartDate = timeLeftUntilStartDate + defineAlertTimeLeftUntilStartDate(taskStatus, alertStartDate)
    }

    scheduleARecurrentMessageAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, reminderTitle, reminderColor)
  }

  def deleteTask(idOfTask: String): Unit = {
    listOfScheduledTask.filter(e => e._1 == idOfTask).map(e => e._2.cancel())
    listOfScheduledTask = listOfScheduledTask.filter(e => e._1 != idOfTask)
  }

  private def postMessageOnSlack(channelId: String, taskDescription: String, message: String, title: String, color: String) = {
    s.slackClient.postMessage(
      ChatMessage(channelId, "")
        .addAttachment(
          AttachmentField(fallback = s"LunAdmin Reminder - $taskDescription", callback_id = "")
            .withText(message)
            .withTitle(title)
            .withColor(s"#$color")
        ))
  }

  private def makeMessage(taskDescription: String, slackUser: User, startDate: ZonedDateTime): String = {
    taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
      startDate.withZoneSameInstant(
        ZoneId.of(slackUser.tz.get)))
  }

  private def findUserAndMessageHim(mail: String, taskId: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String) = {
    s.slackClient.userLookupByEmail(mail).map { slackUser =>
      val message: String = makeMessage(taskDescription, slackUser, startDate)
      postMessageOnSlack(slackUser.id, taskDescription, message, title, color)
    }
  }

  private def defineAlertTimeLeftUntilStartDate(taskStatus: String, startDate: ZonedDateTime): Long = {
    taskStatus match {
      case "Quotidienne" => 1.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli) % 1.day.toMillis
      case "Hebdomadaire" => 7.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli) % 7.day.toMillis
      case "Mensuel" => 30.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli) % 30.day.toMillis
      case "Annuel" => 365.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli) % 365.day.toMillis
    }
  }

  private def getDurationFromStatus(taskStatus: String): FiniteDuration = {
    taskStatus match {
      case "Quotidienne" => 1 day
      case "Hebdomadaire" => 7 days
      case "Mensuel" => 30 days
      case "Annuel" => 365 days
    }
  }

  private def getAlertStartDateFromStartDate(startDate: ZonedDateTime, alertDuration: Long): ZonedDateTime = {
    ZonedDateTime.from(
      startDate.minusSeconds(alertDuration / 1000)
    )
  }

  private def scheduleARecurrentMessageAndAddToCancellableList(taskId: String, taskStatus: String, timeLeftUntilStartDate: Long, mail: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String): Unit = {
    var movableStartDate : ZonedDateTime = startDate
    val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, getDurationFromStatus(taskStatus)) {
      movableStartDate = movableStartDate.plusSeconds(getDurationFromStatus(taskStatus).toSeconds)
      findUserAndMessageHim(mail, taskId, taskDescription, movableStartDate, reminderTitle, reminderColor)
    }
    listOfScheduledTask = listOfScheduledTask :+ (taskId, scheduledTask)
  }

  private def scheduleAMessageOnceAndAddToCancellableList(taskId: String, taskStatus: String, timeLeftUntilStartDate: Long, mail: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String): Unit = {
    val scheduledTask = actorSystem.scheduler.scheduleOnce(timeLeftUntilStartDate millis) {
      findUserAndMessageHim(mail, taskId, taskDescription, startDate, missionTitle, importantColor)
    }
    listOfScheduledTask = listOfScheduledTask :+ (taskId, scheduledTask)
  }
}
