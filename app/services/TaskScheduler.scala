package services

import java.time.format.TextStyle
import java.time.{ZoneId, ZonedDateTime}
import java.util.Locale

import actor.SchedulerActor
import akka.actor._
import com.lunatech.slack.client.models.{AttachmentField, ChatMessage, User}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import controllers.Starter
import javax.inject.{Inject, Singleton}
import models.{GroupedTask, SinglePersonTask}
import play.api.Configuration
import reactivemongo.bson.BSONObjectID
import tools.DateUtils

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

@Singleton
class TaskScheduler @Inject()(s: Starter, conf: Configuration)(implicit actorSystem: ActorSystem, ec: ExecutionContext) {

  var listOfUniqueScheduledTask: List[(String, Cancellable)] = List()
  var listOfRecurrentScheduledTask : List[(String,String)] = List()

  val reminderTitle: String = "Reminder"
  val missionTitle: String = "Your Mission"
  val communColor: String = "85DF2D"
  val importantColor: String = "BB2100"
  val recurrentColor: String = "FFA316"
  val reminderColor: String = "00BABD"

  val scheduler = QuartzSchedulerExtension(actorSystem)

  def setSlackBotMessageForSingleTask(task: SinglePersonTask): Unit = {
    s.userDataStore.findUserById(task.employeeId).foreach { optUser =>
      optUser.foreach { user =>
        if (Seq("Unique - Common", "Unique - Important").contains(task.status)) {
          scheduleASlackBotMessageOnce(task._id, user.mail, task.startDate, task.status, task.description)
          for (alert <- task.alert) {
            scheduleASlackBotAlertMessageOnce(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        } else {
          scheduleASlackBotMessageRecurrent(task._id, user.mail, task.startDate, task.endDate, task.status, task.description)
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
        if (Seq("Unique - Common", "Unique - Important").contains(task.status)) {
          scheduleASlackBotMessageOnce(task._id, user.mail, task.startDate, task.status, task.description)
          for (alert <- task.alert) {
            scheduleASlackBotAlertMessageOnce(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        } else {
          scheduleASlackBotMessageRecurrent(task._id, user.mail, task.startDate, task.endDate, task.status, task.description)
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
      case "Unique - Common" =>
        scheduleAMessageOnceAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, missionTitle, communColor)
      case "Unique - Important" =>
        scheduleAMessageOnceAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, missionTitle, importantColor)
    }
  }

  private def scheduleASlackBotAlertMessageOnce(taskId: String, mail: String, startDate: ZonedDateTime, taskStatus: String, taskDescription: String, alertDuration: Long): Unit = {
    val alertStartDate = getAlertStartDateFromStartDate(startDate, alertDuration)

    val timeLeftUntilStartDate: Long = alertStartDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli

    scheduleAMessageOnceAndAddToCancellableList(taskId, taskStatus, timeLeftUntilStartDate, mail, taskDescription, startDate, reminderTitle, reminderColor)
  }

  private def scheduleASlackBotMessageRecurrent(taskId: String, mail: String, startDate: ZonedDateTime,endDate : ZonedDateTime, taskStatus: String, taskDescription: String): Unit = {
    val cronExpression = getCronExpression(startDate,taskStatus)
    passElementToActor(taskId,taskStatus,mail,taskDescription,startDate,missionTitle,recurrentColor,cronExpression)
    scheduleEndOfMessage(taskId,endDate)
  }

  private def scheduleASlackBotAlertMessageRecurrent(taskId: String, mail: String, startDate: ZonedDateTime, taskStatus: String, taskDescription: String, alertDuration: Long): Unit = {
    val alertStartDate = getAlertStartDateFromStartDate(startDate, alertDuration)
    val cronExpression = getCronExpression(alertStartDate,taskStatus)
    passElementToActor(taskId,taskStatus,mail,taskDescription,alertStartDate,missionTitle,recurrentColor,cronExpression)
  }

  def deleteTask(idOfTask: String): Unit = {
    listOfUniqueScheduledTask.filter(e => e._1 == idOfTask).map(e => e._2.cancel())
    listOfUniqueScheduledTask = listOfUniqueScheduledTask.filter(e => e._1 != idOfTask)

    listOfRecurrentScheduledTask.filter(e => e._1 == idOfTask).map(e => scheduler.cancelJob(e._2))
    listOfRecurrentScheduledTask = listOfRecurrentScheduledTask.filter(e => e._1 != idOfTask)
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

  private def makeRecurrentMessage(taskDescription : String, slackUser : User, startDate : ZonedDateTime, taskStatus : String) = {
    taskStatus match{
      case "Daily" => s"$taskDescription - $taskStatus - Every day at ${startDate.getHour}h${startDate.getMinute}"
      case "Weekly" => s"$taskDescription - $taskStatus - Every ${startDate.getDayOfWeek.getDisplayName(TextStyle.FULL,Locale.ENGLISH)} at ${startDate.getHour}h${startDate.getMinute}"
      case "Monthly" => s"$taskDescription - $taskStatus - The ${startDate.getDayOfMonth} day of every month at ${startDate.getHour}h${startDate.getMinute}"
      case "Yearly" => s"$taskDescription - $taskStatus - The ${startDate.getDayOfMonth} of ${startDate.getMonth.getDisplayName(TextStyle.FULL,Locale.ENGLISH)} at ${startDate.getHour}h${startDate.getMinute} every year"
    }
  }

  def findUserAndMessageHimOnce(mail: String, taskId: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String) : Unit = {
    s.slackClient.userLookupByEmail(mail).map { slackUser =>
      val message: String = makeMessage(taskDescription, slackUser, startDate)
      postMessageOnSlack(slackUser.id, taskDescription, message, title, color)
    }
  }

  def findUserAndMessageHimRecurrent(mail: String, taskId: String,taskStatus : String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String) : Unit = {
    s.slackClient.userLookupByEmail(mail).map { slackUser =>
      val message: String = makeRecurrentMessage(taskDescription, slackUser, startDate, taskStatus)
      postMessageOnSlack(slackUser.id, taskDescription, message, title, color)
    }
  }

  private def getAlertStartDateFromStartDate(startDate: ZonedDateTime, alertDuration: Long): ZonedDateTime = {
    ZonedDateTime.from(
      startDate.minusSeconds(alertDuration / 1000)
    )
  }

  private def passElementToActor(taskId: String, taskStatus: String, mail: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String, cronExpression: String) = {
    val nameOfSchedule = BSONObjectID.generate().stringify
    scheduler.createSchedule(name = nameOfSchedule,cronExpression = cronExpression)
    val actor = actorSystem.actorOf(Props(new SchedulerActor(s,this)), nameOfSchedule)
    listOfRecurrentScheduledTask = listOfRecurrentScheduledTask :+ (taskId.toString,nameOfSchedule)

    scheduler.schedule(nameOfSchedule,actor, (taskId,
                                              taskStatus,
                                              mail,
                                              taskDescription,
                                              startDate,
                                              title,
                                              color))
  }

  private def scheduleAMessageOnceAndAddToCancellableList(taskId: String, taskStatus: String, timeLeftUntilStartDate: Long, mail: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String): Unit = {
    val scheduledTask = actorSystem.scheduler.scheduleOnce(timeLeftUntilStartDate millis) {
      findUserAndMessageHimOnce(mail, taskId, taskDescription, startDate, title, color)
    }
    listOfUniqueScheduledTask = listOfUniqueScheduledTask :+ (taskId, scheduledTask)
  }

  private def getCronExpression(startDate : ZonedDateTime, taskStatus : String) : String = {
    taskStatus match{
      case "Daily" => s"${startDate.getSecond} ${startDate.getMinute} ${startDate.getHour} * * ? *"
      case "Weekly" => s"${startDate.getSecond} ${startDate.getMinute} ${startDate.getHour} ? * ${startDate.getDayOfWeek.getValue}"
      case "Monthly" => s"${startDate.getSecond} ${startDate.getMinute} ${startDate.getHour} ${startDate.getDayOfMonth} * ?"
      case "Yearly" => s"${startDate.getSecond} ${startDate.getMinute} ${startDate.getHour} ${startDate.getDayOfMonth} ${startDate.getMonth.getValue/12} ?"
    }
  }

  private def scheduleEndOfMessage(taskId : String,endDate : ZonedDateTime) : Unit = {
    val delay = endDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
    val scheduledEnd = actorSystem.scheduler.scheduleOnce(delay millis){
      deleteTask(taskId)
    }
    listOfUniqueScheduledTask = listOfUniqueScheduledTask :+ (taskId, scheduledEnd)
  }

  def updateSingleTask(idOfTask : String, task : SinglePersonTask) : Unit = {
    deleteTask(idOfTask)
    setSlackBotMessageForSingleTask(task)
  }

  def updateGroupedTask(idOfTask : String, task : GroupedTask) : Unit = {
    deleteTask(idOfTask)
    setSlackBotMessageForGroupedTask(task)
  }

}
