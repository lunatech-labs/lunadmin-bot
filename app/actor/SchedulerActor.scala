package actor

import java.time.ZonedDateTime

import akka.actor._
import controllers.Starter
import javax.inject._
import services.TaskScheduler

object SchedulerActor {
  def props: Props = Props[SchedulerActor]
}

class SchedulerActor @Inject()(s: Starter, taskScheduler: TaskScheduler) extends Actor {
  def receive = {
    case (taskId: String, taskStatus: String, mail: String, taskDescription: String, startDate: ZonedDateTime, title: String, color: String) =>
      taskScheduler.findUserAndMessageHimRecurrent(mail, taskId, taskStatus, taskDescription, startDate, title, color)
  }
}


