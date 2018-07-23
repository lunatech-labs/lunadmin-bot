package services

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.{ActorSystem, Cancellable}
import com.lunatech.slack.client.models.{AttachmentField, ChatEphemeral}
import controllers.Starter
import javax.inject.{Inject, Singleton}
import models.{GroupedTask, SinglePersonTask}
import play.api.Configuration
import tools.DateUtils
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

@Singleton
class TaskScheduler @Inject()(s : Starter,conf : Configuration) (implicit actorSystem: ActorSystem, ec : ExecutionContext) {

  var listOfScheduledTask : List[(String, Cancellable)] = List()

  def setSlackBotMessageForSingleTask(task : SinglePersonTask) : Unit = {
    s.userDataStore.findUserById(task.employeeId).foreach{ optUser =>
      optUser.foreach{ user =>
        if(Seq("Unique - Commune","Unique - Importante").contains(task.status)){
          scheduleASlackBotMessageOnce(task._id, user.mail, task.startDate, task.status , task.description)
          for(alert <- task.alert){
            scheduleASlackBotAlertMessageOnce(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        }else{
          scheduleASlackBotMessageRecurrent(task._id, user.mail, task.startDate, task.status , task.description)
          for(alert <- task.alert){
            scheduleASlackBotAlertMessageRecurrent(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        }
      }
    }
  }

  def setSlackBotMessageForGroupedTask(task : GroupedTask) : Unit = {
    s.userDataStore.findUserDescriptionByListOfUserGroup(task.groupName).map( listOfUser =>
      for(user <- listOfUser){
        if(Seq("Unique - Commune","Unique - Importante").contains(task.status)){
          scheduleASlackBotMessageOnce(task._id, user.mail, task.startDate, task.status , task.description)
          for(alert <- task.alert){
            scheduleASlackBotAlertMessageOnce(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        }else{
          scheduleASlackBotMessageRecurrent(task._id, user.mail, task.startDate, task.status , task.description)
          for(alert <- task.alert){
            scheduleASlackBotAlertMessageRecurrent(task._id, user.mail, task.startDate, task.status, task.description, alert._1)
          }
        }
      }
    )
  }

  private def scheduleASlackBotMessageOnce(taskId : String, mail : String, startDate : ZonedDateTime, taskStatus : String, taskDescription: String) : Unit = {
    val timeLeftUntilStartDate : Long = startDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli

    taskStatus match {
      case "Unique - Commune" =>
        val scheduledTask = actorSystem.scheduler.scheduleOnce(timeLeftUntilStartDate millis){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

              s.slackClient.postEphemeral(
                ChatEphemeral(slackUser.id, "", slackUser.id)
                  .addAttachment(
                    AttachmentField(fallback = s"LunAdmin - $taskDescription", callback_id = "commun task")
                      .withText(message)
                      .withTitle("Your Mission")
                      .withColor("#85DF2D")
                  ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)

      case "Unique - Importante" =>
        val scheduledTask = actorSystem.scheduler.scheduleOnce(timeLeftUntilStartDate millis){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

              s.slackClient.postEphemeral(
                ChatEphemeral(slackUser.id, "", slackUser.id)
                  .addAttachment(
                    AttachmentField(fallback = s"LunAdmin - $taskDescription", callback_id = "important task")
                      .withText(message)
                      .withTitle("Your Mission")
                      .withColor("#BB2100")
                  ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)

    }
  }

  private def scheduleASlackBotAlertMessageOnce(taskId : String, mail : String, startDate : ZonedDateTime, taskStatus : String, taskDescription: String, alertDuration : Long) : Unit = {
    val alertStartDate = ZonedDateTime.from(
      startDate.minusSeconds(alertDuration/1000)
    )
    val timeLeftUntilStartDate : Long = alertStartDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli

    taskStatus match {
      case "Unique - Commune" =>
        val scheduledTask = actorSystem.scheduler.scheduleOnce(timeLeftUntilStartDate millis){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin Reminder - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Reminder")
                  .withColor("#00BABD")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)

      case "Unique - Importante" =>
        val scheduledTask = actorSystem.scheduler.scheduleOnce(timeLeftUntilStartDate millis){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

              s.slackClient.postEphemeral(
                ChatEphemeral(slackUser.id, "", slackUser.id)
                  .addAttachment(
                    AttachmentField(fallback = s"LunAdmin Reminder - $taskDescription", callback_id = "important task")
                      .withText(message)
                      .withTitle("Reminder")
                      .withColor("#00BABD")
                  ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)

    }
  }

  private def scheduleASlackBotMessageRecurrent(taskId : String, mail : String, startDate : ZonedDateTime, taskStatus : String, taskDescription: String) : Unit = {
    var timeLeftUntilStartDate : Long = 0
    if(ZonedDateTime.now().toInstant.toEpochMilli <= startDate.toInstant.toEpochMilli){
      timeLeftUntilStartDate = timeLeftUntilStartDate + startDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
    }else {
      taskStatus match{
        case "Quotidienne" => timeLeftUntilStartDate = timeLeftUntilStartDate + (1.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli)%1.day.toMillis)
        case "Hebdomadaire" => timeLeftUntilStartDate = timeLeftUntilStartDate + (7.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli)%7.day.toMillis)
        case "Mensuel" => timeLeftUntilStartDate = timeLeftUntilStartDate + (30.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli)%30.day.toMillis)
        case "Annuel" => timeLeftUntilStartDate = timeLeftUntilStartDate + (365.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - startDate.toInstant.toEpochMilli)%365.day.toMillis)
      }
    }

    taskStatus match{
      case "Quotidienne" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 1 day){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Your Mission")
                  .withColor("#FFA316")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)


      case "Hebdomadaire" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 7 days){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Your Mission")
                  .withColor("#FFA316")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)


      case "Mensuel" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 30 days){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Your Mission")
                  .withColor("#FFA316")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)


      case "Annuel" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 365 days){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Your Mission")
                  .withColor("#FFA316")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)

    }
  }

  private def scheduleASlackBotAlertMessageRecurrent(taskId : String, mail : String, startDate : ZonedDateTime, taskStatus : String, taskDescription: String, alertDuration : Long) : Unit = {
    var timeLeftUntilStartDate : Long = 0
    val alertStartDate = ZonedDateTime.from(
      startDate.minusSeconds(alertDuration/1000)
    )

    if(ZonedDateTime.now().getDayOfYear <= alertStartDate.getDayOfYear){
      timeLeftUntilStartDate = timeLeftUntilStartDate + alertStartDate.toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
    }else {
      taskStatus match{
        case "Quotidienne" => timeLeftUntilStartDate = timeLeftUntilStartDate + (1.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - alertStartDate.toInstant.toEpochMilli)%1.day.toMillis)
        case "Hebdomadaire" => timeLeftUntilStartDate = timeLeftUntilStartDate + (7.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - alertStartDate.toInstant.toEpochMilli)%7.day.toMillis)
        case "Mensuel" => timeLeftUntilStartDate = timeLeftUntilStartDate + (30.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - alertStartDate.toInstant.toEpochMilli)%30.day.toMillis)
        case "Annuel" => timeLeftUntilStartDate = timeLeftUntilStartDate + (365.day.toMillis - (ZonedDateTime.now().toInstant.toEpochMilli - alertStartDate.toInstant.toEpochMilli)%365.day.toMillis)
      }
    }

    taskStatus match{
      case "Quotidienne" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 1 day){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin Reminder - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Reminder")
                  .withColor("#85DF2D")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)


      case "Hebdomadaire" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 7 days){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin Reminder - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Reminder")
                  .withColor("#85DF2D")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)


      case "Mensuel" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 30 days){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin Reminder- $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Reminder")
                  .withColor("#85DF2D")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)


      case "Annuel" =>
        val scheduledTask = actorSystem.scheduler.schedule(timeLeftUntilStartDate millis, 365 days){
          s.slackClient.userLookupByEmail(mail).map { slackUser =>
            val message : String = taskDescription + " - " + DateUtils.dateTimeFormatterLocal.format(
              startDate.withZoneSameInstant(
                ZoneId.of(slackUser.tz.get)))

          s.slackClient.postEphemeral(
            ChatEphemeral(slackUser.id, "", slackUser.id)
              .addAttachment(
                AttachmentField(fallback = s"LunAdmin Reminder - $taskDescription", callback_id = "commun task")
                  .withText(message)
                  .withTitle("Reminder")
                  .withColor("#85DF2D")
              ))
          }
        }
        listOfScheduledTask = listOfScheduledTask :+ (taskId,scheduledTask)
      }
    }

  def deleteTask(idOfTask : String) : Unit = {
    listOfScheduledTask.filter(e => e._1 == idOfTask).map(e => e._2.cancel())
    listOfScheduledTask = listOfScheduledTask.filter(e => e._1 != idOfTask)
  }

}
